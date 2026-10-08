package com.carddemo.domain.fixedwidth;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Builds one fixed-width record; unset fields (including FILLER) are spaces. */
public final class RecordWriter {

    private final RecordLayout layout;
    private final char[] buffer;

    RecordWriter(RecordLayout layout) {
        this.layout = layout;
        this.buffer = " ".repeat(layout.length()).toCharArray();
    }

    public RecordWriter text(String field, String value) {
        FieldSpec spec = layout.field(field);
        put(spec, spec.encodeText(value));
        return this;
    }

    public RecordWriter number(String field, BigDecimal value) {
        FieldSpec spec = layout.field(field);
        put(spec, spec.encodeNumber(value));
        return this;
    }

    public RecordWriter number(String field, long value) {
        return number(field, BigDecimal.valueOf(value));
    }

    public RecordWriter date(String field, LocalDate value) {
        return text(field, value == null ? "" : value.toString());
    }

    private void put(FieldSpec spec, String encoded) {
        encoded.getChars(0, spec.length(), buffer, spec.offset());
    }

    @Override
    public String toString() {
        return new String(buffer);
    }
}
