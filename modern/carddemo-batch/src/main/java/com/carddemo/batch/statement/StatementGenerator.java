package com.carddemo.batch.statement;

import com.carddemo.batch.support.CobolEdit;
import com.carddemo.batch.support.LegacyAbendException;
import com.carddemo.batch.support.RecordSink;
import com.carddemo.domain.fixedwidth.CobolDecimal;
import com.carddemo.domain.fixedwidth.FixedWidth;
import com.carddemo.domain.fixedwidth.ZonedDecimal;
import com.carddemo.domain.model.Account;
import com.carddemo.domain.model.CardXref;
import com.carddemo.domain.model.Customer;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Account statement generation (CBSTM03A, file access as in CBSTM03B).
 * <p>Requirements: BAT-STM-01 (docs/BUSINESS_REQUIREMENTS.md).
 */
public final class StatementGenerator {

    /** CBSTM03B keyed reads (M03B-READ-K) of CUSTFILE and ACCTFILE. */
    public interface Lookups {
        Optional<Customer> customer(long customerId);

        Optional<Account> account(long accountId);
    }

    /** CBSTM03A 9999-ABEND-PROGRAM: {@code CALL 'CEE3ABD'} with no abend code. */
    static final int ABEND_CODE = 0;

    private static final String TD_END = "</td>";
    private static final String TR_START = "<tr>";
    private static final String TR_END = "</tr>";
    private static final String TD_BANNER = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#1d1d96b3;\">";
    private static final String TD_BANK = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#FFAF33;\">";
    private static final String TD_GREY = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#f2f2f2;\">";
    private static final String TD_SECTION = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#33FFD1; text-align:center;\">";
    private static final String TD_HEAD_ID = "<td style=\"width:25%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">";
    private static final String TD_HEAD_DETAIL = "<td style=\"width:55%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">";
    private static final String TD_HEAD_AMOUNT = "<td style=\"width:20%; padding:0px 5px; background-color:#33FF5E; text-align:right;\">";
    private static final String TD_ROW_ID = "<td style=\"width:25%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">";
    private static final String TD_ROW_DETAIL = "<td style=\"width:55%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">";
    private static final String TD_ROW_AMOUNT = "<td style=\"width:20%; padding:0px 5px; background-color:#f2f2f2; text-align:right;\">";
    private static final String DASHES = "-".repeat(80);
    static final int HTML_LRECL = 100;

    private final Lookups lookups;
    private final boolean unboundedTable;
    private final boolean escapeHtml;

    public StatementGenerator(Lookups lookups, boolean unboundedTable, boolean escapeHtml) {
        this.lookups = lookups;
        this.unboundedTable = unboundedTable;
        this.escapeHtml = escapeHtml;
    }

    /**
     * CBSTM03A 0000-START / 1000-MAINLINE.
     *
     * @param trnxRecords TRXFL records (card + transaction id key, 350 bytes) in key order
     * @param xrefs       card cross references in card-number order
     * @return number of statements written
     */
    public int generate(List<String> trnxRecords, Iterator<CardXref> xrefs, RecordSink statement, RecordSink html) {
        StatementTable table = loadTransactions(trnxRecords);
        int statements = 0;
        while (xrefs.hasNext()) {
            CardXref xref = xrefs.next();
            Customer customer = lookups.customer(xref.getCustomerId()).orElseThrow(() -> abend(
                    "CUSTFILE record " + xref.getCustomerId() + " not found for card " + xref.getCardNumber()));
            Account account = lookups.account(xref.getAccountId()).orElseThrow(() -> abend(
                    "ACCTFILE record " + xref.getAccountId() + " not found for card " + xref.getCardNumber()));
            createStatement(customer, account, statement, html);
            writeTransactions(table, xref.getCardNumber(), statement, html);
            statements++;
        }
        return statements;
    }

    /** CBSTM03A 8100-TRNXFILE-OPEN and 8500-READTRNX-READ: buffer all transactions by card. */
    private StatementTable loadTransactions(List<String> records) {
        if (records.isEmpty()) {
            throw abend("TRNXFILE is empty (CBSTM03B read RC=10 on first read)");
        }
        StatementTable table = unboundedTable ? sizedFor(records)
                : new StatementTable(StatementTable.LEGACY_CARDS, StatementTable.LEGACY_TRANSACTIONS);
        String saveCard = records.get(0).substring(0, 16);
        int cr = 1;
        int tr = 0;
        for (String record : records) {
            String card = record.substring(0, 16);
            if (saveCard.equals(card)) {
                tr++;
            } else {
                table.setCount(cr, tr);
                cr++;
                tr = 1;
            }
            table.putCard(cr, card);
            table.putTransaction(cr, tr, record.substring(16, 32), record.substring(32, 350));
            saveCard = card;
        }
        table.setCount(cr, tr);
        cardCount = cr;
        return table;
    }

    private int cardCount;

    private static StatementTable sizedFor(List<String> records) {
        int cards = 0;
        int max = 0;
        int run = 0;
        String previous = null;
        for (String record : records) {
            String card = record.substring(0, 16);
            if (!card.equals(previous)) {
                cards++;
                run = 0;
                previous = card;
            }
            max = Math.max(max, ++run);
        }
        return new StatementTable(cards, max);
    }

    /** CBSTM03A 4000-TRNXFILE-GET */
    private void writeTransactions(StatementTable table, String xrefCard, RecordSink statement, RecordSink html) {
        BigDecimal total = BigDecimal.ZERO;
        for (int cr = 1; cr <= cardCount && table.card(cr).compareTo(xrefCard) <= 0; cr++) {
            if (xrefCard.equals(table.card(cr))) {
                for (int tr = 1; tr <= table.count(cr); tr++) {
                    String id = table.transactionId(cr, tr);
                    String rest = table.transactionRest(cr, tr);
                    BigDecimal amount = ZonedDecimal.decode(rest.substring(116, 127), 2, true);
                    writeTransaction(id, rest.substring(16, 116), amount, statement, html);
                    total = CobolDecimal.s9v2(total.add(amount));
                }
            }
        }
        statement.write(DASHES);
        statement.write("Total EXP:" + " ".repeat(56) + "$" + CobolEdit.format(total, "ZZZZZZZZZ.99-"));
        statement.write("*".repeat(32) + "END OF STATEMENT" + "*".repeat(32));
        for (String line : List.of(TR_START, TD_BANNER, "<h3>End of Statement</h3>", TD_END, TR_END,
                "</table>", "</body>", "</html>")) {
            html.write(line);
        }
    }

    /** CBSTM03A 5000-CREATE-STATEMENT */
    private void createStatement(Customer customer, Account account, RecordSink statement, RecordSink html) {
        String name = FixedWidth.padRight(firstWord(customer.getFirstName(), 25) + " " + firstWord(customer.getMiddleName(), 25)
                + " " + firstWord(customer.getLastName(), 25) + " ", 75);
        String add1 = FixedWidth.padRight(customer.getAddressLine1(), 50);
        String add2 = FixedWidth.padRight(customer.getAddressLine2(), 50);
        String add3 = FixedWidth.padRight(firstWord(customer.getAddressLine3(), 50) + " " + firstWord(customer.getStateCode(), 2)
                + " " + firstWord(customer.getCountryCode(), 3) + " " + firstWord(customer.getZip(), 10) + " ", 80);
        String acctId = FixedWidth.padRight("%011d".formatted(account.getId()), 20);
        String balance = CobolEdit.format(account.getCurrentBalance(), "999999999.99-");
        String fico = FixedWidth.padRight("%03d".formatted(customer.getFicoCreditScore() % 1000), 20);

        statement.write("*".repeat(31) + "START OF STATEMENT" + "*".repeat(31));
        writeHtmlHeader(acctId, html);
        writeHtmlNameAddressBasics(name, add1, add2, add3, acctId, balance, fico, html);
        statement.write(name);
        statement.write(add1);
        statement.write(add2);
        statement.write(add3);
        statement.write(DASHES);
        statement.write(" ".repeat(33) + FixedWidth.padRight("Basic Details", 14));
        statement.write(DASHES);
        statement.write("Account ID         :" + acctId);
        statement.write("Current Balance    :" + balance);
        statement.write("FICO Score         :" + fico);
        statement.write(DASHES);
        statement.write(" ".repeat(30) + "TRANSACTION SUMMARY ");
        statement.write(DASHES);
        statement.write(FixedWidth.padRight("Tran ID         ", 16) + FixedWidth.padRight("Tran Details    ", 51) + "  Tran Amount");
        statement.write(DASHES);
    }

    /** CBSTM03A 5100-WRITE-HTML-HEADER */
    private void writeHtmlHeader(String acctId, RecordSink html) {
        for (String line : List.of("<!DOCTYPE html>", "<html lang=\"en\">", "<head>", "<meta charset=\"utf-8\">",
                "<title>HTML Table Layout</title>", "</head>", "<body style=\"margin:0px;\">",
                "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">",
                TR_START, TD_BANNER, "<h3>Statement for Account Number: " + acctId + "</h3>", TD_END, TR_END,
                TR_START, TD_BANK, "<p style=\"font-size:16px\">Bank of XYZ</p>", "<p>410 Terry Ave N</p>",
                "<p>Seattle WA 99999</p>", TD_END, TR_END, TR_START, TD_GREY)) {
            html.write(line);
        }
    }

    /** CBSTM03A 5200-WRITE-HTML-NMADBS */
    private void writeHtmlNameAddressBasics(String name, String add1, String add2, String add3, String acctId,
                                            String balance, String fico, RecordSink html) {
        html.write(htmlLine("<p style=\"font-size:16px\">", untilDoubleSpace(name.substring(0, 50)), "  </p>"));
        html.write(htmlLine("<p>", untilDoubleSpace(add1), "  </p>"));
        html.write(htmlLine("<p>", untilDoubleSpace(add2), "  </p>"));
        html.write(htmlLine("<p>", untilDoubleSpace(add3), "  </p>"));
        for (String line : List.of(TD_END, TR_END, TR_START, TD_SECTION, "<p style=\"font-size:16px\">Basic Details</p>",
                TD_END, TR_END, TR_START, TD_GREY)) {
            html.write(line);
        }
        html.write("<p>Account ID         : " + acctId + "</p>");
        html.write("<p>Current Balance    : " + balance + "</p>");
        html.write("<p>FICO Score         : " + fico + "</p>");
        for (String line : List.of(TD_END, TR_END, TR_START, TD_SECTION,
                "<p style=\"font-size:16px\">Transaction Summary</p>", TD_END, TR_END, TR_START,
                TD_HEAD_ID, "<p style=\"font-size:16px\">Tran ID</p>", TD_END,
                TD_HEAD_DETAIL, "<p style=\"font-size:16px\">Tran Details</p>", TD_END,
                TD_HEAD_AMOUNT, "<p style=\"font-size:16px\">Amount</p>", TD_END, TR_END)) {
            html.write(line);
        }
    }

    /** CBSTM03A 6000-WRITE-TRANS */
    private void writeTransaction(String id, String description, BigDecimal amount, RecordSink statement, RecordSink html) {
        String detail = description.substring(0, 49);
        String edited = CobolEdit.format(amount, "ZZZZZZZZZ.99-");
        statement.write(id + " " + detail + "$" + edited);
        html.write(TR_START);
        html.write(TD_ROW_ID);
        html.write(htmlLine("<p>", id, "</p>"));
        html.write(TD_END);
        html.write(TD_ROW_DETAIL);
        html.write(htmlLine("<p>", detail, "</p>"));
        html.write(TD_END);
        html.write(TD_ROW_AMOUNT);
        html.write(htmlLine("<p>", edited, "</p>"));
        html.write(TD_END);
        html.write(TR_END);
    }

    /** {@code STRING field DELIMITED BY ' '}: characters up to the first space of the padded field. */
    private static String firstWord(String value, int length) {
        String padded = FixedWidth.padRight(value, length);
        int space = padded.indexOf(' ');
        return space < 0 ? padded : padded.substring(0, space);
    }

    /**
     * CBSTM03A STRINGs data fields into the HTML lines as-is (LEGACY-DEFECTS #9). With
     * {@code escape-statement-html} on, the field is HTML-escaped and shortened to fit the 100-byte record.
     */
    private String htmlLine(String prefix, String field, String suffix) {
        return escapeHtml ? escapedLine(prefix, field, suffix, HTML_LRECL) : prefix + field + suffix;
    }

    static String escapedLine(String prefix, String field, String suffix, int lrecl) {
        String value = field;
        String escaped = escapeHtml(value);
        while (!value.isEmpty() && prefix.length() + escaped.length() + suffix.length() > lrecl) {
            value = value.substring(0, value.length() - 1);
            escaped = escapeHtml(value);
        }
        return prefix + escaped + suffix;
    }

    static String escapeHtml(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** {@code STRING field DELIMITED BY '  '} */
    private static String untilDoubleSpace(String padded) {
        int stop = padded.indexOf("  ");
        return stop < 0 ? padded : padded.substring(0, stop);
    }

    private static LegacyAbendException abend(String message) {
        return new LegacyAbendException(ABEND_CODE, message);
    }
}
