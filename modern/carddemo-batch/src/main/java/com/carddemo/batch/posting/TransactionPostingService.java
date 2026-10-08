package com.carddemo.batch.posting;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.domain.fixedwidth.CobolDecimal;
import com.carddemo.domain.model.Account;
import com.carddemo.domain.model.CardTransaction;
import com.carddemo.domain.model.CardXref;
import com.carddemo.domain.model.CategoryBalanceId;
import com.carddemo.domain.model.TransactionCategoryBalance;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.TransactionCategoryBalanceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Daily transaction validation and posting (CBTRN02C).
 * <p>Requirements: BAT-POST-01, BAT-POST-02, BAT-POST-03 (docs/BUSINESS_REQUIREMENTS.md).
 */
@Service
public class TransactionPostingService {

    private final CardXrefRepository xrefs;
    private final AccountRepository accounts;
    private final TransactionCategoryBalanceRepository categoryBalances;
    private final Clock clock;
    private final CardDemoBatchProperties.LegacyFixes fixes;

    public TransactionPostingService(CardXrefRepository xrefs, AccountRepository accounts,
                                     TransactionCategoryBalanceRepository categoryBalances, Clock clock,
                                     CardDemoBatchProperties properties) {
        this.xrefs = xrefs;
        this.accounts = accounts;
        this.categoryBalances = categoryBalances;
        this.clock = clock;
        this.fixes = properties.legacyFixes();
    }

    /** CBTRN02C main loop body: 1500-VALIDATE-TRAN then 2000-POST-TRANSACTION or 2500-WRITE-REJECT-REC. */
    public PostingOutcome process(DailyTransaction daily) {
        CardTransaction t = daily.transaction();
        Optional<CardXref> xref = xrefs.findById(t.getCardNumber());
        if (xref.isEmpty()) {
            return PostingOutcome.rejected(daily, RejectReason.INVALID_CARD);
        }
        Optional<Account> account = accounts.findById(xref.get().getAccountId());
        if (account.isEmpty()) {
            return PostingOutcome.rejected(daily, RejectReason.ACCOUNT_NOT_FOUND);
        }
        RejectReason reason = validateAccount(account.get(), t);
        if (reason != null) {
            return PostingOutcome.rejected(daily, reason);
        }
        return PostingOutcome.posted(daily, post(t, xref.get(), account.get()));
    }

    /**
     * CBTRN02C 1500-B-LOOKUP-ACCT. Both checks always run and the later one wins, so an
     * over-limit transaction on an expired account is reported as 103 (LEGACY-DEFECTS #1).
     */
    RejectReason validateAccount(Account account, CardTransaction t) {
        RejectReason reason = null;
        BigDecimal exposure = fixes.limitCheckUsesCurrentBalance()
                ? CobolDecimal.s9v2(account.getCurrentBalance().add(t.getAmount()))
                : CobolDecimal.s9v2(account.getCurrentCycleCredit().subtract(account.getCurrentCycleDebit()).add(t.getAmount()));
        if (account.getCreditLimit().compareTo(exposure) < 0) {
            reason = RejectReason.OVERLIMIT;
        }
        String transactionDate = t.getOriginatedAtFormat().format(t.getOriginatedAt()).substring(0, 10);
        String expiry = account.getExpirationDate() == null ? " ".repeat(10) : account.getExpirationDate().toString();
        if (expiry.compareTo(transactionDate) < 0 && !(fixes.keepOverlimitReason() && reason != null)) {
            reason = RejectReason.ACCOUNT_EXPIRED;
        }
        return reason;
    }

    /** CBTRN02C 2000-POST-TRANSACTION */
    private CardTransaction post(CardTransaction t, CardXref xref, Account account) {
        LocalDateTime processedAt = LocalDateTime.now(clock);
        updateCategoryBalance(xref.getAccountId(), t);
        updateAccount(account, t.getAmount());
        t.setProcessedAt(processedAt);
        return t;
    }

    /** CBTRN02C 2700-UPDATE-TCATBAL (2700-A-CREATE-TCATBAL-REC / 2700-B-UPDATE-TCATBAL-REC) */
    void updateCategoryBalance(long accountId, CardTransaction t) {
        CategoryBalanceId key = new CategoryBalanceId(accountId, t.getTypeCode(), t.getCategoryCode());
        Optional<TransactionCategoryBalance> existing = categoryBalances.findById(key);
        if (existing.isPresent()) {
            existing.get().setBalance(CobolDecimal.s9v2(existing.get().getBalance().add(t.getAmount())));
        } else {
            categoryBalances.save(new TransactionCategoryBalance(key, CobolDecimal.s9v2(t.getAmount())));
        }
    }

    /** CBTRN02C 2800-UPDATE-ACCOUNT-REC */
    void updateAccount(Account account, BigDecimal amount) {
        account.setCurrentBalance(CobolDecimal.s10v2(account.getCurrentBalance().add(amount)));
        if (amount.signum() >= 0) {
            account.setCurrentCycleCredit(CobolDecimal.s10v2(account.getCurrentCycleCredit().add(amount)));
        } else {
            account.setCurrentCycleDebit(CobolDecimal.s10v2(account.getCurrentCycleDebit().add(amount)));
        }
    }
}
