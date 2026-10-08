package com.carddemo.batch.posting;

/**
 * CBTRN02C WS-VALIDATION-FAIL-REASON / -DESC values.
 * <p>Requirements: BAT-POST-01, BAT-POST-02 (docs/BUSINESS_REQUIREMENTS.md).
 */
public enum RejectReason {
    INVALID_CARD(100, "INVALID CARD NUMBER FOUND"),
    ACCOUNT_NOT_FOUND(101, "ACCOUNT RECORD NOT FOUND"),
    OVERLIMIT(102, "OVERLIMIT TRANSACTION"),
    ACCOUNT_EXPIRED(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");

    private final int code;
    private final String description;

    RejectReason(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() { return code; }

    public String description() { return description; }
}
