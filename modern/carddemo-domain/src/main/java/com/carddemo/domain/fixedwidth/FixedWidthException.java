package com.carddemo.domain.fixedwidth;

public class FixedWidthException extends RuntimeException {

    public FixedWidthException(String message) {
        super(message);
    }

    public FixedWidthException(String message, Throwable cause) {
        super(message, cause);
    }
}
