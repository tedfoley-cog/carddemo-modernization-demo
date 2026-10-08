package com.carddemo.online.seed;

import com.carddemo.online.legacy.ZonedDecimal;
import java.math.BigDecimal;

/** Field accessor over one fixed-width record, using 1-based copybook offsets. */
final class FixedWidthRecord {
    private final String rec;
    private int pos;

    FixedWidthRecord(String rec) {
        this.rec = rec;
    }

    String x(int len) {
        int start = Math.min(pos, rec.length());
        int end = Math.min(pos + len, rec.length());
        pos += len;
        return rec.substring(start, end).stripTrailing();
    }

    long n(int len) {
        String v = x(len).trim();
        return v.isEmpty() ? 0 : Long.parseLong(v);
    }

    BigDecimal s(int digits, int scale) {
        return ZonedDecimal.decode(x(digits), scale);
    }

    void skip(int len) {
        pos += len;
    }
}
