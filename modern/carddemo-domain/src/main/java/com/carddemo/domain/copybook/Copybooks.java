package com.carddemo.domain.copybook;

import com.carddemo.domain.fixedwidth.RecordLayout;

/** Record layouts transcribed from app/cpy. Field names are the copybook data names. */
public final class Copybooks {

    public static final RecordLayout CVACT01Y = RecordLayout.builder("CVACT01Y")
            .unsigned("ACCT-ID", 11).text("ACCT-ACTIVE-STATUS", 1)
            .signed("ACCT-CURR-BAL", 12, 2).signed("ACCT-CREDIT-LIMIT", 12, 2).signed("ACCT-CASH-CREDIT-LIMIT", 12, 2)
            .text("ACCT-OPEN-DATE", 10).text("ACCT-EXPIRAION-DATE", 10).text("ACCT-REISSUE-DATE", 10)
            .signed("ACCT-CURR-CYC-CREDIT", 12, 2).signed("ACCT-CURR-CYC-DEBIT", 12, 2)
            .text("ACCT-ADDR-ZIP", 10).text("ACCT-GROUP-ID", 10).filler(178).build();

    public static final RecordLayout CVACT02Y = RecordLayout.builder("CVACT02Y")
            .text("CARD-NUM", 16).unsigned("CARD-ACCT-ID", 11).unsigned("CARD-CVV-CD", 3)
            .text("CARD-EMBOSSED-NAME", 50).text("CARD-EXPIRAION-DATE", 10).text("CARD-ACTIVE-STATUS", 1)
            .filler(59).build();

    public static final RecordLayout CVACT03Y = RecordLayout.builder("CVACT03Y")
            .text("XREF-CARD-NUM", 16).unsigned("XREF-CUST-ID", 9).unsigned("XREF-ACCT-ID", 11).filler(14).build();

    public static final RecordLayout CVCUS01Y = RecordLayout.builder("CVCUS01Y")
            .unsigned("CUST-ID", 9).text("CUST-FIRST-NAME", 25).text("CUST-MIDDLE-NAME", 25).text("CUST-LAST-NAME", 25)
            .text("CUST-ADDR-LINE-1", 50).text("CUST-ADDR-LINE-2", 50).text("CUST-ADDR-LINE-3", 50)
            .text("CUST-ADDR-STATE-CD", 2).text("CUST-ADDR-COUNTRY-CD", 3).text("CUST-ADDR-ZIP", 10)
            .text("CUST-PHONE-NUM-1", 15).text("CUST-PHONE-NUM-2", 15).unsigned("CUST-SSN", 9)
            .text("CUST-GOVT-ISSUED-ID", 20).text("CUST-DOB-YYYY-MM-DD", 10).text("CUST-EFT-ACCOUNT-ID", 10)
            .text("CUST-PRI-CARD-HOLDER-IND", 1).unsigned("CUST-FICO-CREDIT-SCORE", 3).filler(168).build();

    public static final RecordLayout CVTRA01Y = RecordLayout.builder("CVTRA01Y")
            .unsigned("TRANCAT-ACCT-ID", 11).text("TRANCAT-TYPE-CD", 2).unsigned("TRANCAT-CD", 4)
            .signed("TRAN-CAT-BAL", 11, 2).filler(22).build();

    public static final RecordLayout CVTRA02Y = RecordLayout.builder("CVTRA02Y")
            .text("DIS-ACCT-GROUP-ID", 10).text("DIS-TRAN-TYPE-CD", 2).unsigned("DIS-TRAN-CAT-CD", 4)
            .signed("DIS-INT-RATE", 6, 2).filler(28).build();

    public static final RecordLayout CVTRA03Y = RecordLayout.builder("CVTRA03Y")
            .text("TRAN-TYPE", 2).text("TRAN-TYPE-DESC", 50).filler(8).build();

    public static final RecordLayout CVTRA04Y = RecordLayout.builder("CVTRA04Y")
            .text("TRAN-TYPE-CD", 2).unsigned("TRAN-CAT-CD", 4).text("TRAN-CAT-TYPE-DESC", 50).filler(4).build();

    /** Transaction master record; DALYTRAN (CVTRA06Y) and SYSTRAN share this geometry. */
    public static final RecordLayout CVTRA05Y = RecordLayout.builder("CVTRA05Y")
            .text("TRAN-ID", 16).text("TRAN-TYPE-CD", 2).unsigned("TRAN-CAT-CD", 4).text("TRAN-SOURCE", 10)
            .text("TRAN-DESC", 100).signed("TRAN-AMT", 11, 2).unsigned("TRAN-MERCHANT-ID", 9)
            .text("TRAN-MERCHANT-NAME", 50).text("TRAN-MERCHANT-CITY", 50).text("TRAN-MERCHANT-ZIP", 10)
            .text("TRAN-CARD-NUM", 16).text("TRAN-ORIG-TS", 26).text("TRAN-PROC-TS", 26).filler(20).build();

    public static final RecordLayout CSUSR01Y = RecordLayout.builder("CSUSR01Y")
            .text("SEC-USR-ID", 8).text("SEC-USR-FNAME", 20).text("SEC-USR-LNAME", 20).text("SEC-USR-PWD", 8)
            .text("SEC-USR-TYPE", 1).text("SEC-USR-FILLER", 23).build();

    private Copybooks() {
    }
}
