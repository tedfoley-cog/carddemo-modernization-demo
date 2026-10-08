package com.carddemo.batch.report;

import com.carddemo.batch.support.CobolEdit;
import com.carddemo.batch.support.RecordSink;
import com.carddemo.domain.fixedwidth.CobolDecimal;
import com.carddemo.domain.fixedwidth.FixedWidth;
import com.carddemo.domain.model.CardTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily transaction report writer (CBTRN03C, layouts from CVTRA07Y). Stateful control-break
 * logic: account totals on card change, page totals every 20 lines, grand total at end.
 */
public final class TransactionReport {

    static final int LRECL = 133;
    private static final int PAGE_SIZE = 20;
    private static final String DASHES = "-".repeat(LRECL);
    private static final String AMOUNT_PIC = "-ZZZ,ZZZ,ZZZ.ZZ";
    private static final String TOTAL_PIC = "+ZZZ,ZZZ,ZZZ.ZZ";

    private final RecordSink sink;
    private final ReportLookups lookups;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final boolean countLastAmountOnce;

    private boolean firstTime = true;
    private long lineCounter;
    private BigDecimal pageTotal = BigDecimal.ZERO;
    private BigDecimal accountTotal = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;
    private String currentCard = "";
    private long xrefAccountId;
    private BigDecimal lastAmount = BigDecimal.ZERO;
    private long detailLines;

    public TransactionReport(RecordSink sink, ReportLookups lookups, LocalDate startDate, LocalDate endDate,
                             boolean countLastAmountOnce) {
        this.sink = sink;
        this.lookups = lookups;
        this.startDate = startDate;
        this.endDate = endDate;
        this.countLastAmountOnce = countLastAmountOnce;
    }

    /** CBTRN03C 0000-MAIN loop body for one transaction inside the date range. */
    public void accept(CardTransaction t) {
        if (!currentCard.equals(t.getCardNumber())) {
            if (!firstTime) {
                writeAccountTotals();
            }
            currentCard = t.getCardNumber();
            xrefAccountId = lookups.accountIdForCard(currentCard);
        }
        String typeDesc = lookups.typeDescription(t.getTypeCode());
        String catDesc = lookups.categoryDescription(t.getTypeCode(), t.getCategoryCode());
        writeTransactionReport(t, typeDesc, catDesc);
        lastAmount = t.getAmount();
    }

    /**
     * CBTRN03C end-of-file branch: TRAN-RECORD still holds the last record read, so its amount
     * is added to the totals a second time; no account total is written for the last card.
     */
    public void finish() {
        if (!countLastAmountOnce) {
            pageTotal = CobolDecimal.s9v2(pageTotal.add(lastAmount));
            accountTotal = CobolDecimal.s9v2(accountTotal.add(lastAmount));
        }
        writePageTotals();
        writeGrandTotals();
    }

    public long detailLines() {
        return detailLines;
    }

    /** CBTRN03C 1100-WRITE-TRANSACTION-REPORT */
    private void writeTransactionReport(CardTransaction t, String typeDesc, String catDesc) {
        if (firstTime) {
            firstTime = false;
            writeHeaders();
        }
        if (lineCounter % PAGE_SIZE == 0) {
            writePageTotals();
            writeHeaders();
        }
        pageTotal = CobolDecimal.s9v2(pageTotal.add(t.getAmount()));
        accountTotal = CobolDecimal.s9v2(accountTotal.add(t.getAmount()));
        writeDetail(t, typeDesc, catDesc);
    }

    /** CBTRN03C 1110-WRITE-PAGE-TOTALS */
    private void writePageTotals() {
        write(FixedWidth.padRight("Page Total", 11) + ".".repeat(86) + CobolEdit.format(pageTotal, TOTAL_PIC));
        grandTotal = CobolDecimal.s9v2(grandTotal.add(pageTotal));
        pageTotal = BigDecimal.ZERO;
        lineCounter++;
        write(DASHES);
        lineCounter++;
    }

    /** CBTRN03C 1120-WRITE-ACCOUNT-TOTALS */
    private void writeAccountTotals() {
        write(FixedWidth.padRight("Account Total", 13) + ".".repeat(84) + CobolEdit.format(accountTotal, TOTAL_PIC));
        accountTotal = BigDecimal.ZERO;
        lineCounter++;
        write(DASHES);
        lineCounter++;
    }

    /** CBTRN03C 1110-WRITE-GRAND-TOTALS */
    private void writeGrandTotals() {
        write(FixedWidth.padRight("Grand Total", 11) + ".".repeat(86) + CobolEdit.format(grandTotal, TOTAL_PIC));
    }

    /** CBTRN03C 1120-WRITE-HEADERS */
    private void writeHeaders() {
        write(FixedWidth.padRight("DALYREPT", 38) + FixedWidth.padRight("Daily Transaction Report", 41)
                + FixedWidth.padRight("Date Range: ", 12) + startDate + " to " + endDate);
        lineCounter++;
        write("");
        lineCounter++;
        write(FixedWidth.padRight("Transaction ID", 17) + FixedWidth.padRight("Account ID", 12)
                + FixedWidth.padRight("Transaction Type", 19) + FixedWidth.padRight("Tran Category", 35)
                + FixedWidth.padRight("Tran Source", 14) + " " + FixedWidth.padRight("        Amount", 16));
        lineCounter++;
        write(DASHES);
        lineCounter++;
    }

    /** CBTRN03C 1120-WRITE-DETAIL */
    private void writeDetail(CardTransaction t, String typeDesc, String catDesc) {
        write(FixedWidth.padRight(t.getTransactionId(), 16) + " "
                + "%011d".formatted(xrefAccountId) + " "
                + FixedWidth.padRight(t.getTypeCode(), 2) + "-"
                + FixedWidth.padRight(typeDesc, 15) + " "
                + "%04d".formatted(t.getCategoryCode()) + "-"
                + FixedWidth.padRight(catDesc, 29) + " "
                + FixedWidth.padRight(t.getSource(), 10) + "    "
                + CobolEdit.format(t.getAmount(), AMOUNT_PIC) + "  ");
        lineCounter++;
        detailLines++;
    }

    private void write(String line) {
        sink.write(FixedWidth.padRight(line, LRECL));
    }
}
