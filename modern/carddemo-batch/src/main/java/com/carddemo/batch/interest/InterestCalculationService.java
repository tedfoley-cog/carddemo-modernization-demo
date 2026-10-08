package com.carddemo.batch.interest;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.batch.support.LegacyAbendException;
import com.carddemo.domain.fixedwidth.CobolDecimal;
import com.carddemo.domain.model.Account;
import com.carddemo.domain.model.CardTransaction;
import com.carddemo.domain.model.CardXref;
import com.carddemo.domain.model.DisclosureGroup;
import com.carddemo.domain.model.DisclosureGroupId;
import com.carddemo.domain.model.TimestampFormat;
import com.carddemo.domain.model.TransactionCategoryBalance;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TransactionCategoryBalanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly interest calculation (CBACT04C). Account rewrites are part of one transaction, so an
 * abend leaves ACCTDATA as it was, while interest transactions already written to SYSTRAN stay
 * written, matching the legacy dataset state after a U0999.
 * <p>Requirements: BAT-INT-01, BAT-INT-02, BAT-INT-03 (docs/BUSINESS_REQUIREMENTS.md).
 */
@Service
public class InterestCalculationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InterestCalculationService.class);

    static final int ABEND_CODE = 999;
    private static final BigDecimal MONTHS_TIMES_PERCENT = BigDecimal.valueOf(1200);
    private static final Sort TCATBAL_KEY = Sort.by("id.accountId", "id.typeCode", "id.categoryCode");

    private final TransactionCategoryBalanceRepository categoryBalances;
    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final DisclosureGroupRepository disclosureGroups;
    private final Clock clock;
    private final CardDemoBatchProperties properties;

    public InterestCalculationService(TransactionCategoryBalanceRepository categoryBalances, AccountRepository accounts,
                                      CardXrefRepository xrefs, DisclosureGroupRepository disclosureGroups, Clock clock,
                                      CardDemoBatchProperties properties) {
        this.categoryBalances = categoryBalances;
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.disclosureGroups = disclosureGroups;
        this.clock = clock;
        this.properties = properties;
    }

    /** CBACT04C PROCEDURE DIVISION main loop. Returns the number of TCATBAL records read. */
    @Transactional
    public long calculate(Consumer<CardTransaction> systemTransactions) {
        Run run = new Run(systemTransactions);
        long read = 0;
        for (TransactionCategoryBalance balance : categoryBalances.findAll(TCATBAL_KEY)) {
            read++;
            long accountId = balance.getId().accountId();
            if (run.account == null || run.account.getId() != accountId) {
                if (run.account != null) {
                    updateAccount(run);
                }
                run.totalInterest = BigDecimal.ZERO;
                run.account = accountData(accountId);
                run.xref = xrefData(accountId);
            }
            BigDecimal rate = interestRate(run.account.getGroupId(), balance.getId().typeCode(), balance.getId().categoryCode());
            if (rate.signum() != 0) {
                computeInterest(run, balance.getBalance(), rate);
                computeFees();
            }
        }
        // The EOF branch of the PERFORM UNTIL is unreachable once the last READ sets END-OF-FILE,
        // so the last account's interest is never rewritten (LEGACY-DEFECTS #6).
        if (run.account != null && properties.legacyFixes().updateLastInterestAccount()) {
            updateAccount(run);
        }
        return read;
    }

    /** CBACT04C 1050-UPDATE-ACCOUNT */
    private void updateAccount(Run run) {
        Account a = run.account;
        a.setCurrentBalance(CobolDecimal.s10v2(a.getCurrentBalance().add(run.totalInterest)));
        a.setCurrentCycleCredit(BigDecimal.ZERO);
        a.setCurrentCycleDebit(BigDecimal.ZERO);
    }

    /** CBACT04C 1100-GET-ACCT-DATA */
    private Account accountData(long accountId) {
        return accounts.findById(accountId).orElseThrow(() ->
                new LegacyAbendException(ABEND_CODE, "ACCOUNT NOT FOUND: " + accountId));
    }

    /** CBACT04C 1110-GET-XREF-DATA (alternate key FD-XREF-ACCT-ID) */
    private CardXref xrefData(long accountId) {
        return xrefs.findFirstByAccountIdOrderByCardNumber(accountId).orElseThrow(() ->
                new LegacyAbendException(ABEND_CODE, "ACCOUNT NOT FOUND IN XREF: " + accountId));
    }

    /** CBACT04C 1200-GET-INTEREST-RATE and 1200-A-GET-DEFAULT-INT-RATE */
    BigDecimal interestRate(String accountGroupId, String typeCode, int categoryCode) {
        Optional<DisclosureGroup> specific = findDisclosureGroup(
                new DisclosureGroupId(accountGroupId, typeCode, categoryCode));
        if (specific.isPresent()) {
            return specific.get().getInterestRate();
        }
        Optional<DisclosureGroup> fallback = findDisclosureGroup(
                new DisclosureGroupId(DisclosureGroupId.DEFAULT_GROUP, typeCode, categoryCode));
        if (fallback.isPresent()) {
            return fallback.get().getInterestRate();
        }
        if (properties.legacyFixes().skipMissingDisclosureGroup()) {
            return BigDecimal.ZERO;
        }
        throw new LegacyAbendException(ABEND_CODE, "DISCLOSURE GROUP RECORD MISSING: DEFAULT/" + typeCode + "/" + categoryCode);
    }

    /**
     * CBACT04C 0200-DISCGRP-OPEN: an unreadable disclosure-group file is reported with the
     * DALY REJECTS message (LEGACY-DEFECTS #8) unless the message fix is enabled.
     */
    private Optional<DisclosureGroup> findDisclosureGroup(DisclosureGroupId id) {
        try {
            return disclosureGroups.findById(id);
        } catch (org.springframework.dao.DataAccessException e) {
            log.error(properties.legacyFixes().correctDisclosureOpenMessage()
                    ? "ERROR READING DISCLOSURE GROUP FILE" : "ERROR OPENING DALY REJECTS FILE", e);
            throw e;
        }
    }

    /** CBACT04C 1300-COMPUTE-INTEREST: truncating COMPUTE into WS-MONTHLY-INT PIC S9(09)V99. */
    static BigDecimal monthlyInterest(BigDecimal categoryBalance, BigDecimal annualRatePercent) {
        return CobolDecimal.s9v2(categoryBalance.multiply(annualRatePercent).divide(MONTHS_TIMES_PERCENT, 2, RoundingMode.DOWN));
    }

    private void computeInterest(Run run, BigDecimal categoryBalance, BigDecimal rate) {
        BigDecimal monthly = monthlyInterest(categoryBalance, rate);
        run.totalInterest = CobolDecimal.s9v2(run.totalInterest.add(monthly));
        writeTransaction(run, monthly);
    }

    /** CBACT04C 1300-B-WRITE-TX */
    private void writeTransaction(Run run, BigDecimal monthlyInterest) {
        run.suffix = (run.suffix + 1) % 1_000_000;
        LocalDateTime now = LocalDateTime.now(clock);
        CardTransaction t = new CardTransaction();
        t.setTransactionId(properties.interestRunId() + "%06d".formatted(run.suffix));
        t.setTypeCode("01");
        t.setCategoryCode(5);
        t.setSource("System");
        t.setDescription("Int. for a/c " + "%011d".formatted(run.account.getId()));
        t.setAmount(monthlyInterest);
        t.setMerchantId(0L);
        t.setMerchantName("");
        t.setMerchantCity("");
        t.setMerchantZip("");
        t.setCardNumber(run.xref.getCardNumber());
        t.setOriginatedAt(now);
        t.setOriginatedAtFormat(TimestampFormat.DB2);
        t.setProcessedAt(now);
        run.sink.accept(t);
    }

    /** CBACT04C 1400-COMPUTE-FEES: an empty paragraph in the legacy program ("To be implemented"). */
    private void computeFees() {
        // intentionally empty, as in CBACT04C
    }

    private static final class Run {
        private final Consumer<CardTransaction> sink;
        private Account account;
        private CardXref xref;
        private BigDecimal totalInterest = BigDecimal.ZERO;
        private int suffix;

        private Run(Consumer<CardTransaction> sink) {
            this.sink = sink;
        }
    }
}
