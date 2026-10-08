package com.carddemo.domain.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Text renderings of timestamps found in CVTRA05Y {@code PIC X(26)} fields. Upstream feeds
 * use ISO with a space separator; values stamped by the batch programs use the DB2 format
 * built by {@code Z-GET-DB2-FORMAT-TIMESTAMP}.
 */
public enum TimestampFormat {
    ISO("yyyy-MM-dd HH:mm:ss.SSSSSS"),
    DB2("yyyy-MM-dd-HH.mm.ss.SSSSSS");

    private final DateTimeFormatter formatter;

    TimestampFormat(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    public String format(LocalDateTime value) {
        return formatter.format(value);
    }

    public LocalDateTime parse(String text) {
        return LocalDateTime.parse(text, formatter);
    }

    public static TimestampFormat detect(String text) {
        return text.length() > 10 && text.charAt(10) == '-' ? DB2 : ISO;
    }
}
