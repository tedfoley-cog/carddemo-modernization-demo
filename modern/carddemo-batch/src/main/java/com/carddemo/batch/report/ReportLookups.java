package com.carddemo.batch.report;

/** Keyed reads CBTRN03C performs per detail line; implementations abend on INVALID KEY. */
public interface ReportLookups {

    /** CBTRN03C 1500-A-LOOKUP-XREF: account id for a card. */
    long accountIdForCard(String cardNumber);

    /** CBTRN03C 1500-B-LOOKUP-TRANTYPE */
    String typeDescription(String typeCode);

    /** CBTRN03C 1500-C-LOOKUP-TRANCATG */
    String categoryDescription(String typeCode, int categoryCode);
}
