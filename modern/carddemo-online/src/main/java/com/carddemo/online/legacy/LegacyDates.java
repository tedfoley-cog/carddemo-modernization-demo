package com.carddemo.online.legacy;

import com.carddemo.domain.model.CardTransaction;
import com.carddemo.domain.model.TimestampFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Converts between the shared typed schema and the PIC X(10) dates / X(26) timestamps the BMS maps show. */
public final class LegacyDates {
    private LegacyDates() {
    }

    public static String text(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    public static LocalDate date(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s.trim());
    }

    /** TRAN-ORIG-TS rendered in the format it arrived in (ISO feed or DB2 stamp). */
    public static String origTs(CardTransaction t) {
        return t.getOriginatedAt() == null ? "" : t.getOriginatedAtFormat().format(t.getOriginatedAt());
    }

    /** TRAN-PROC-TS: the batch and online writers stamp it in DB2 format. */
    public static String procTs(CardTransaction t) {
        return t.getProcessedAt() == null ? "" : TimestampFormat.DB2.format(t.getProcessedAt());
    }

    /** Sets TRAN-ORIG-TS / TRAN-PROC-TS from their legacy X(26) text. */
    public static void stamp(CardTransaction t, String orig, String proc) {
        TimestampFormat of = TimestampFormat.detect(orig.trim());
        t.setOriginatedAt(timestamp(of, orig));
        t.setOriginatedAtFormat(of);
        t.setProcessedAt(proc == null || proc.isBlank() ? null : timestamp(TimestampFormat.detect(proc.trim()), proc));
    }

    /** COTRN02C moves a bare YYYY-MM-DD into the X(26) field; it is kept as midnight of that day. */
    private static LocalDateTime timestamp(TimestampFormat f, String text) {
        String s = text.trim();
        return s.length() == 10 ? LocalDate.parse(s).atStartOfDay() : f.parse(s);
    }
}
