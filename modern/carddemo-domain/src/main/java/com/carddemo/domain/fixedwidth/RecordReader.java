package com.carddemo.domain.fixedwidth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Typed, read-only view over one fixed-width record. */
public final class RecordReader {

    private final RecordLayout layout;
    private final String record;

    RecordReader(RecordLayout layout, String record) {
        this.layout = layout;
        this.record = record;
    }

    public String raw() {
        return record;
    }

    /** {@code PIC X} content with trailing padding removed. */
    public String text(String field) {
        return FixedWidth.trimTrailing(layout.field(field).slice(record));
    }

    public BigDecimal decimal(String field) {
        return layout.field(field).decodeNumber(record);
    }

    public long longValue(String field) {
        return decimal(field).longValueExact();
    }

    public int intValue(String field) {
        return decimal(field).intValueExact();
    }

    /** ISO {@code YYYY-MM-DD} held in {@code PIC X(10)}; blank means not set. */
    public LocalDate date(String field) {
        String value = text(field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new FixedWidthException(layout.copybook() + "." + field + ": invalid date '" + value + "'", e);
        }
    }
}
