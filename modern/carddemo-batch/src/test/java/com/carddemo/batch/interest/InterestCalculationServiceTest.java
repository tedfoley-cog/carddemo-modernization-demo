package com.carddemo.batch.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.batch.TestProperties;
import com.carddemo.batch.config.CardDemoBatchProperties.LegacyFixes;
import com.carddemo.batch.support.LegacyAbendException;
import com.carddemo.domain.model.Account;
import com.carddemo.domain.model.CardTransaction;
import com.carddemo.domain.model.CardXref;
import com.carddemo.domain.model.CategoryBalanceId;
import com.carddemo.domain.model.DisclosureGroup;
import com.carddemo.domain.model.DisclosureGroupId;
import com.carddemo.domain.model.TransactionCategoryBalance;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TransactionCategoryBalanceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class InterestCalculationServiceTest {

    private final TransactionCategoryBalanceRepository balances = mock(TransactionCategoryBalanceRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final CardXrefRepository xrefs = mock(CardXrefRepository.class);
    private final DisclosureGroupRepository groups = mock(DisclosureGroupRepository.class);
    private final Clock clock = Clock.fixed(TestProperties.BUSINESS_TS.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final List<CardTransaction> systran = new ArrayList<>();
    private final Account first = account(1L);
    private final Account last = account(2L);

    private static Account account(long id) {
        Account a = new Account();
        a.setId(id);
        a.setGroupId("GOLD");
        a.setCurrentBalance(new BigDecimal("100.00"));
        a.setCurrentCycleCredit(new BigDecimal("5.00"));
        a.setCurrentCycleDebit(new BigDecimal("3.00"));
        return a;
    }

    private static CardXref xref(long accountId) {
        CardXref x = new CardXref();
        x.setCardNumber("400000000000000" + accountId);
        x.setAccountId(accountId);
        x.setCustomerId(accountId);
        return x;
    }

    private void rate(String group, String rate) {
        DisclosureGroup g = mock(DisclosureGroup.class);
        when(g.getInterestRate()).thenReturn(new BigDecimal(rate));
        when(groups.findById(new DisclosureGroupId(group, "01", 1))).thenReturn(Optional.of(g));
    }

    private InterestCalculationService service(LegacyFixes fixes) {
        return new InterestCalculationService(balances, accounts, xrefs, groups, clock, TestProperties.with(fixes));
    }

    private static LegacyFixes fixes(boolean skipMissingGroup, boolean updateLast) {
        return new LegacyFixes(false, false, skipMissingGroup, false, false, false, updateLast, false);
    }

    @BeforeEach
    void setUp() {
        when(balances.findAll(any(Sort.class))).thenReturn(List.of(
                new TransactionCategoryBalance(new CategoryBalanceId(1L, "01", 1), new BigDecimal("1000.00")),
                new TransactionCategoryBalance(new CategoryBalanceId(2L, "01", 1), new BigDecimal("500.00"))));
        when(accounts.findById(1L)).thenReturn(Optional.of(first));
        when(accounts.findById(2L)).thenReturn(Optional.of(last));
        when(xrefs.findFirstByAccountIdOrderByCardNumber(1L)).thenReturn(Optional.of(xref(1L)));
        when(xrefs.findFirstByAccountIdOrderByCardNumber(2L)).thenReturn(Optional.of(xref(2L)));
        when(groups.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void batInt01_monthlyInterestIsBalanceTimesRateOver1200Truncated() {
        assertThat(InterestCalculationService.monthlyInterest(new BigDecimal("1000.00"), new BigDecimal("15.00")))
                .isEqualByComparingTo("12.50");
        // 333.33 * 17.99 / 1200 = 4.99716... -> truncated, not rounded
        assertThat(InterestCalculationService.monthlyInterest(new BigDecimal("333.33"), new BigDecimal("17.99")))
                .isEqualByComparingTo("4.99");
        assertThat(InterestCalculationService.monthlyInterest(new BigDecimal("-333.33"), new BigDecimal("17.99")))
                .isEqualByComparingTo("-4.99");
    }

    @Test
    void batInt01_writesSystemTransactionsAndUpdatesAccountOnAccountChange() {
        rate("GOLD", "12.00");
        long read = service(fixes(false, false)).calculate(systran::add);

        assertThat(read).isEqualTo(2);
        assertThat(systran).hasSize(2);
        CardTransaction t = systran.get(0);
        assertThat(t.getTransactionId()).isEqualTo("2022071800000001");
        assertThat(t.getTypeCode()).isEqualTo("01");
        assertThat(t.getCategoryCode()).isEqualTo(5);
        assertThat(t.getAmount()).isEqualByComparingTo("10.00");
        assertThat(t.getCardNumber()).isEqualTo("4000000000000001");
        assertThat(t.getProcessedAt()).isEqualTo(TestProperties.BUSINESS_TS);
        assertThat(first.getCurrentBalance()).isEqualByComparingTo("110.00");
        assertThat(first.getCurrentCycleCredit()).isZero();
        assertThat(first.getCurrentCycleDebit()).isZero();
    }

    @Test
    void batInt01_lastAccountIsNotUpdatedUnlessFixEnabled() {
        rate("GOLD", "12.00");
        service(fixes(false, false)).calculate(systran::add);
        assertThat(last.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(last.getCurrentCycleCredit()).isEqualByComparingTo("5.00");

        service(fixes(false, true)).calculate(t -> { });
        assertThat(last.getCurrentBalance()).isEqualByComparingTo("105.00");
    }

    @Test
    void batInt02_fallsBackToDefaultDisclosureGroup() {
        rate(DisclosureGroupId.DEFAULT_GROUP, "24.00");
        assertThat(service(fixes(false, false)).interestRate("GOLD", "01", 1)).isEqualByComparingTo("24.00");
    }

    @Test
    void batInt02_specificGroupWinsOverDefault() {
        rate("GOLD", "6.00");
        rate(DisclosureGroupId.DEFAULT_GROUP, "24.00");
        assertThat(service(fixes(false, false)).interestRate("GOLD", "01", 1)).isEqualByComparingTo("6.00");
    }

    @Test
    void batInt02_missingSpecificAndDefaultAbendsU0999() {
        assertThatThrownBy(() -> service(fixes(false, false)).calculate(systran::add))
                .isInstanceOfSatisfying(LegacyAbendException.class, e -> assertThat(e.formattedCode()).isEqualTo("U0999"));
        assertThat(service(fixes(true, false)).interestRate("GOLD", "01", 1)).isZero();
    }

    @Test
    void batInt03_zeroRateWritesNoTransactionAndNoFee() {
        rate("GOLD", "0.00");
        service(fixes(false, false)).calculate(systran::add);
        assertThat(systran).isEmpty();
    }

    @Test
    void batInt01_missingXrefAbends() {
        rate("GOLD", "12.00");
        when(xrefs.findFirstByAccountIdOrderByCardNumber(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(fixes(false, false)).calculate(systran::add))
                .isInstanceOf(LegacyAbendException.class);
    }
}
