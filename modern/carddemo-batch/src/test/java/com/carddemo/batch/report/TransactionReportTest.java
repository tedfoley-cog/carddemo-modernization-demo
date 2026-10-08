package com.carddemo.batch.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.domain.model.CardTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionReportTest {

    private final List<String> lines = new ArrayList<>();

    private final ReportLookups lookups = new ReportLookups() {
        @Override
        public long accountIdForCard(String cardNumber) {
            return 1L;
        }

        @Override
        public String typeDescription(String typeCode) {
            return "Purchase";
        }

        @Override
        public String categoryDescription(String typeCode, int categoryCode) {
            return "Regular Sales Draft";
        }
    };

    @Test
    void batRpt01_lastAmountIsCountedTwiceAtEndOfFileByDefault() {
        TransactionReport report = report(false);
        report.accept(txn("0000000000000001", "4111111111111111", "10.00"));
        report.accept(txn("0000000000000002", "4111111111111111", "5.00"));
        report.finish();

        assertThat(lines.get(lines.size() - 1).stripTrailing()).startsWith("Grand Total").endsWith("20.00");
        assertThat(lines).allSatisfy(l -> assertThat(l).hasSize(TransactionReport.LRECL));
        assertThat(report.detailLines()).isEqualTo(2);
    }

    @Test
    void batRpt01_lastAmountIsCountedOnceWhenFixEnabled() {
        TransactionReport report = report(true);
        report.accept(txn("0000000000000001", "4111111111111111", "10.00"));
        report.accept(txn("0000000000000002", "4111111111111111", "5.00"));
        report.finish();

        assertThat(lines.get(lines.size() - 1).stripTrailing()).startsWith("Grand Total").endsWith("15.00");
    }

    @Test
    void batRpt01_accountTotalOnCardChangeButNotForLastCard() {
        TransactionReport report = report(false);
        report.accept(txn("0000000000000001", "4111111111111111", "10.00"));
        report.accept(txn("0000000000000002", "4222222222222222", "7.00"));
        report.finish();

        assertThat(lines).filteredOn(l -> l.startsWith("Account Total")).singleElement()
                .satisfies(l -> assertThat(l.stripTrailing()).endsWith("10.00"));
    }

    private TransactionReport report(boolean countLastAmountOnce) {
        return new TransactionReport(lines::add, lookups, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 7, 6),
                countLastAmountOnce);
    }

    private static CardTransaction txn(String id, String card, String amount) {
        CardTransaction t = new CardTransaction();
        t.setTransactionId(id);
        t.setTypeCode("01");
        t.setCategoryCode(1);
        t.setSource("POS TERM");
        t.setDescription("Purchase at store");
        t.setAmount(new BigDecimal(amount));
        t.setMerchantId(800000001L);
        t.setMerchantName("Store");
        t.setMerchantCity("City");
        t.setMerchantZip("12345");
        t.setCardNumber(card);
        t.setOriginatedAt(LocalDateTime.of(2022, 6, 1, 10, 0));
        t.setProcessedAt(LocalDateTime.of(2022, 6, 1, 10, 0));
        return t;
    }
}
