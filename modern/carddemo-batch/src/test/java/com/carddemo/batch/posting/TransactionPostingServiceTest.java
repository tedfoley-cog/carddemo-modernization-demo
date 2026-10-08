package com.carddemo.batch.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.batch.TestProperties;
import com.carddemo.batch.config.CardDemoBatchProperties.LegacyFixes;
import com.carddemo.domain.model.Account;
import com.carddemo.domain.model.CardTransaction;
import com.carddemo.domain.model.CardXref;
import com.carddemo.domain.model.CategoryBalanceId;
import com.carddemo.domain.model.TimestampFormat;
import com.carddemo.domain.model.TransactionCategoryBalance;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.TransactionCategoryBalanceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionPostingServiceTest {

    private static final String CARD = "4111111111111111";
    private final CardXrefRepository xrefs = mock(CardXrefRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionCategoryBalanceRepository balances = mock(TransactionCategoryBalanceRepository.class);
    private final Clock clock = Clock.fixed(TestProperties.BUSINESS_TS.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private TransactionPostingService service;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new TransactionPostingService(xrefs, accounts, balances, clock, TestProperties.legacy());
        CardXref xref = new CardXref();
        xref.setCardNumber(CARD);
        xref.setAccountId(7L);
        xref.setCustomerId(9L);
        account = new Account();
        account.setId(7L);
        account.setCurrentBalance(new BigDecimal("100.00"));
        account.setCreditLimit(new BigDecimal("1000.00"));
        account.setCurrentCycleCredit(new BigDecimal("600.00"));
        account.setCurrentCycleDebit(new BigDecimal("100.00"));
        account.setExpirationDate(LocalDate.of(2023, 1, 31));
        when(xrefs.findById(CARD)).thenReturn(Optional.of(xref));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(balances.findById(any())).thenReturn(Optional.empty());
    }

    private static DailyTransaction daily(String card, String amount, LocalDateTime originated) {
        CardTransaction t = new CardTransaction();
        t.setTransactionId("0000000000000001");
        t.setTypeCode("01");
        t.setCategoryCode(1);
        t.setCardNumber(card);
        t.setAmount(new BigDecimal(amount));
        t.setOriginatedAt(originated);
        t.setOriginatedAtFormat(TimestampFormat.ISO);
        return new DailyTransaction("RAW".repeat(10), t);
    }

    private static DailyTransaction daily(String amount) {
        return daily(CARD, amount, LocalDateTime.of(2022, 6, 1, 12, 0));
    }

    @Test
    void batPost01_unknownCardIsRejected100() {
        PostingOutcome outcome = service.process(daily("4000000000000000", "1.00", LocalDateTime.of(2022, 6, 1, 0, 0)));
        assertThat(outcome.reject()).isEqualTo(RejectReason.INVALID_CARD);
        assertThat(outcome.rejectRecord()).endsWith("0100INVALID CARD NUMBER FOUND" + " ".repeat(76 - 25)).hasSize(30 + 80);
        verify(accounts, never()).findById(any());
    }

    @Test
    void batPost01_missingAccountIsRejected101() {
        when(accounts.findById(7L)).thenReturn(Optional.empty());
        assertThat(service.process(daily("1.00")).reject()).isEqualTo(RejectReason.ACCOUNT_NOT_FOUND);
    }

    @Test
    void batPost02_limitBoundaryUsesCycleCreditMinusDebit() {
        // 600 - 100 + 500 = 1000 == limit -> accepted; one cent more -> 102
        assertThat(service.validateAccount(account, daily("500.00").transaction())).isNull();
        assertThat(service.validateAccount(account, daily("500.01").transaction())).isEqualTo(RejectReason.OVERLIMIT);
    }

    @Test
    void batPost02_limitCheckIgnoresCurrentBalanceUnlessFixEnabled() {
        account.setCurrentBalance(new BigDecimal("5000.00"));
        assertThat(service.validateAccount(account, daily("1.00").transaction())).isNull();
        TransactionPostingService fixed = new TransactionPostingService(xrefs, accounts, balances, clock,
                TestProperties.with(new LegacyFixes(false, true, false, false, false, false, false, false, false)));
        assertThat(fixed.validateAccount(account, daily("1.00").transaction())).isEqualTo(RejectReason.OVERLIMIT);
    }

    @Test
    void batPost02_workFieldTruncatesHighOrderDigits() {
        // WS-TEMP-BAL PIC S9(09)V99: 500 + 999,999,999.99 wraps to 499.99 and passes the limit
        assertThat(service.validateAccount(account, daily("999999999.99").transaction())).isNull();
    }

    @Test
    void batPost02_expiryBoundaryIsInclusive() {
        assertThat(service.validateAccount(account, daily(CARD, "1.00", LocalDateTime.of(2023, 1, 31, 23, 59)).transaction())).isNull();
        assertThat(service.validateAccount(account, daily(CARD, "1.00", LocalDateTime.of(2023, 2, 1, 0, 0)).transaction()))
                .isEqualTo(RejectReason.ACCOUNT_EXPIRED);
    }

    @Test
    void batPost02_overlimitAndExpiredReports103Only() {
        DailyTransaction both = daily(CARD, "900.00", LocalDateTime.of(2023, 3, 1, 0, 0));
        assertThat(service.validateAccount(account, both.transaction())).isEqualTo(RejectReason.ACCOUNT_EXPIRED);
        TransactionPostingService fixed = new TransactionPostingService(xrefs, accounts, balances, clock,
                TestProperties.with(new LegacyFixes(true, false, false, false, false, false, false, false, false)));
        assertThat(fixed.validateAccount(account, both.transaction())).isEqualTo(RejectReason.OVERLIMIT);
    }

    @Test
    void batPost02_blankExpiryRejects103() {
        account.setExpirationDate(null);
        assertThat(service.validateAccount(account, daily("1.00").transaction())).isEqualTo(RejectReason.ACCOUNT_EXPIRED);
    }

    @Test
    void batPost03_creditGoesToCycleCreditAndCreatesCategoryBalance() {
        PostingOutcome outcome = service.process(daily("25.50"));
        assertThat(outcome.isRejected()).isFalse();
        assertThat(outcome.posted().getProcessedAt()).isEqualTo(TestProperties.BUSINESS_TS);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("125.50");
        assertThat(account.getCurrentCycleCredit()).isEqualByComparingTo("625.50");
        assertThat(account.getCurrentCycleDebit()).isEqualByComparingTo("100.00");
        verify(balances).save(any(TransactionCategoryBalance.class));
    }

    @Test
    void batPost03_debitGoesToCycleDebitAndUpdatesExistingCategoryBalance() {
        TransactionCategoryBalance existing = new TransactionCategoryBalance(new CategoryBalanceId(7L, "01", 1), new BigDecimal("10.00"));
        when(balances.findById(new CategoryBalanceId(7L, "01", 1))).thenReturn(Optional.of(existing));
        service.process(daily("-4.25"));
        assertThat(account.getCurrentCycleDebit()).isEqualByComparingTo("95.75");
        assertThat(account.getCurrentCycleCredit()).isEqualByComparingTo("600.00");
        assertThat(existing.getBalance()).isEqualByComparingTo("5.75");
        verify(balances, never()).save(any());
    }
}
