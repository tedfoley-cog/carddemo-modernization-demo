package com.carddemo.domain.fixedwidth;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Helpers for RECFM=F datasets and {@code PIC X} padding. */
public final class FixedWidth {

    /** Single-byte charset used for every legacy dataset (EBCDIC already translated). */
    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    private FixedWidth() {
    }

    public static String padRight(String value, int length) {
        String v = value == null ? "" : value;
        if (v.length() >= length) {
            return v.substring(0, length);
        }
        return v + " ".repeat(length - v.length());
    }

    /** Text with trailing spaces removed: how {@code PIC X} content is held in the domain. */
    public static String trimTrailing(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    /**
     * Reads an ASCII seed file (one record per line, CR stripped, blank lines skipped) and pads
     * every record to the LRECL, the way the legacy loader builds RECFM=F input for REPRO.
     */
    public static List<String> readLineRecords(Path file, int lrecl) {
        try {
            String content = Files.readString(file, CHARSET);
            List<String> records = new ArrayList<>();
            for (String line : content.split("\n", -1)) {
                String record = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
                if (record.isBlank()) {
                    continue;
                }
                if (record.length() > lrecl) {
                    throw new FixedWidthException(file + ": record longer than LRECL " + lrecl);
                }
                records.add(padRight(record, lrecl));
            }
            return records;
        } catch (IOException e) {
            throw new FixedWidthException("Cannot read " + file, e);
        }
    }

    /** Splits a RECFM=F dataset (no line terminators) into records. */
    public static List<String> readFixedRecords(Path file, int lrecl) {
        try {
            String content = Files.readString(file, CHARSET);
            if (content.length() % lrecl != 0) {
                throw new FixedWidthException(file + ": size is not a multiple of LRECL " + lrecl);
            }
            List<String> records = new ArrayList<>(content.length() / lrecl);
            for (int i = 0; i < content.length(); i += lrecl) {
                records.add(content.substring(i, i + lrecl));
            }
            return records;
        } catch (IOException e) {
            throw new FixedWidthException("Cannot read " + file, e);
        }
    }
}
