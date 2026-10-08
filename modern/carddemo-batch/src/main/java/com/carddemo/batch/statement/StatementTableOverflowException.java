package com.carddemo.batch.statement;

/** The input exceeds what the legacy statement table can address without undefined behaviour. */
public class StatementTableOverflowException extends RuntimeException {

    public StatementTableOverflowException(String message) {
        super(message);
    }
}
