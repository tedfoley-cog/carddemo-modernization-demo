package com.carddemo.domain.fixedwidth;

import java.math.BigDecimal;

/** One elementary item of a copybook record. */
public record FieldSpec(String name, int offset, int length, FieldType type, int scale) {

    public FieldSpec {
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("Bad field geometry for " + name);
        }
    }

    public int end() {
        return offset + length;
    }

    public String slice(CharSequence record) {
        return record.subSequence(offset, end()).toString();
    }

    public BigDecimal decodeNumber(CharSequence record) {
        return ZonedDecimal.decode(slice(record), scale, type == FieldType.SIGNED);
    }

    public String encodeNumber(BigDecimal value) {
        return ZonedDecimal.encode(value, length, scale, type == FieldType.SIGNED);
    }

    public String encodeText(String value) {
        return FixedWidth.padRight(value, length);
    }
}
