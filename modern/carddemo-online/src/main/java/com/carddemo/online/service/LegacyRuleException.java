package com.carddemo.online.service;

/**
 * A business-rule failure carrying the exact legacy screen message (ERRMSGO), the input field the
 * COBOL positions the cursor on, and the paragraph that raised it.
 */
public class LegacyRuleException extends RuntimeException {
    private final String field;
    private final String paragraph;
    private final int status;

    public LegacyRuleException(String message, String field, String paragraph) {
        this(message, field, paragraph, 422);
    }

    public LegacyRuleException(String message, String field, String paragraph, int status) {
        super(message);
        this.field = field;
        this.paragraph = paragraph;
        this.status = status;
    }

    public static LegacyRuleException notFound(String message, String field, String paragraph) {
        return new LegacyRuleException(message, field, paragraph, 404);
    }

    public static LegacyRuleException conflict(String message, String paragraph) {
        return new LegacyRuleException(message, null, paragraph, 409);
    }

    public String getField() {
        return field;
    }

    public String getParagraph() {
        return paragraph;
    }

    public int getStatus() {
        return status;
    }
}
