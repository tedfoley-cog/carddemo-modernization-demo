package com.carddemo.batch.posting;

import com.carddemo.domain.model.CardTransaction;

/** Result of validating and posting one daily transaction. */
public record PostingOutcome(DailyTransaction input, CardTransaction posted, RejectReason reject) {

    public static PostingOutcome posted(DailyTransaction input, CardTransaction posted) {
        return new PostingOutcome(input, posted, null);
    }

    public static PostingOutcome rejected(DailyTransaction input, RejectReason reason) {
        return new PostingOutcome(input, null, reason);
    }

    public boolean isRejected() {
        return reject != null;
    }

    /** CBTRN02C 2500-WRITE-REJECT-REC: DALYTRAN record + reason 9(04) + description X(76). */
    public String rejectRecord() {
        return input.rawRecord() + "%04d".formatted(reject.code())
                + com.carddemo.domain.fixedwidth.FixedWidth.padRight(reject.description(), 76);
    }
}
