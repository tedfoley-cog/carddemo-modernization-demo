package com.carddemo.online.service;

import com.carddemo.online.repo.TransactionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * COTRN02C / COBIL00C: STARTBR TRANSACT at HIGH-VALUES, READPREV, TRAN-ID + 1. The legacy scheme is
 * kept (QUIRK-TRN-01); a transaction-scoped advisory lock serialises concurrent adds so two
 * instances cannot hand out the same id, which CICS only avoided by single-region enqueue.
 */
@Component
public class TransactionIdGenerator {
    private final TransactionRepository transactions;
    private final JdbcTemplate jdbc;

    public TransactionIdGenerator(TransactionRepository transactions, JdbcTemplate jdbc) {
        this.transactions = transactions;
        this.jdbc = jdbc;
    }

    /** Must be called inside the transaction that inserts the row. */
    public String next() {
        jdbc.queryForObject("select pg_advisory_xact_lock(4711)", Object.class);
        long last = transactions.findFirstByOrderByTranIdDesc()
                .map(t -> Long.parseLong(t.getTranId().trim()))
                .orElse(0L);
        return String.format("%016d", last + 1);
    }
}
