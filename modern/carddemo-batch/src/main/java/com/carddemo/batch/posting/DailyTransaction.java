package com.carddemo.batch.posting;

import com.carddemo.domain.model.CardTransaction;

/** One DALYTRAN record: the decoded transaction plus the raw record echoed on DALYREJS. */
public record DailyTransaction(String rawRecord, CardTransaction transaction) {
}
