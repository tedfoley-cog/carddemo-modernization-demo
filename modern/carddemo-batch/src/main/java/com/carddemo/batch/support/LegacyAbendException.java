package com.carddemo.batch.support;

/**
 * Raised where the COBOL program would call CEE3ABD. The user abend code is reported in
 * RETURN-CODES.txt exactly as the legacy runtime reports it (e.g. {@code U0999}).
 */
public class LegacyAbendException extends RuntimeException {

    private final int abendCode;

    public LegacyAbendException(int abendCode, String message) {
        super(message);
        this.abendCode = abendCode;
    }

    public int abendCode() {
        return abendCode;
    }

    public String formattedCode() {
        return "U%04d".formatted(abendCode);
    }
}
