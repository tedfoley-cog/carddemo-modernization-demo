package com.carddemo.batch.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Summary emitted after postTransactionsJob, the hand-off to downstream consumers. */
public record TransactionsPostedEvent(
        String jobName,
        LocalDateTime businessTimestamp,
        long transactionsRead,
        long transactionsPosted,
        long transactionsRejected,
        BigDecimal postedAmount,
        int returnCode) {

    public static final String TYPE = "transactions-posted";
}
