package com.carddemo.batch.support;

import com.carddemo.domain.fixedwidth.CobolDecimal;
import java.math.BigDecimal;

/**
 * Renders numbers through COBOL numeric-edited PICTUREs as used by the report and statement
 * layouts: {@code 9} digit, {@code Z} zero-suppressed digit, {@code ,} and {@code .} insertion,
 * and a fixed leading or trailing {@code +}/{@code -} sign.
 */
public final class CobolEdit {

    private CobolEdit() {
    }

    public static String format(BigDecimal value, String picture) {
        int point = picture.indexOf('.');
        int integerDigits = countDigits(point < 0 ? picture : picture.substring(0, point));
        int fractionDigits = point < 0 ? 0 : countDigits(picture.substring(point));
        BigDecimal v = CobolDecimal.truncate(value, integerDigits, fractionDigits);
        boolean negative = v.signum() < 0;
        String digits = v.abs().unscaledValue().toString();
        digits = "0".repeat(Math.max(0, integerDigits + fractionDigits - digits.length())) + digits;

        if (v.signum() == 0 && picture.indexOf('9') < 0) {
            return " ".repeat(picture.length());
        }
        StringBuilder out = new StringBuilder(picture.length());
        boolean significant = false;
        boolean fraction = false;
        int next = 0;
        for (int i = 0; i < picture.length(); i++) {
            char p = picture.charAt(i);
            switch (p) {
                case '9', 'Z' -> {
                    char d = digits.charAt(next++);
                    if (p == '9' || fraction || d != '0') {
                        significant = true;
                    }
                    out.append(significant ? d : ' ');
                }
                case ',' -> out.append(significant ? ',' : ' ');
                case '.' -> {
                    fraction = true;
                    significant = true;
                    out.append('.');
                }
                case '-' -> out.append(negative ? '-' : ' ');
                case '+' -> out.append(negative ? '-' : '+');
                default -> throw new IllegalArgumentException("Unsupported picture symbol '" + p + "' in " + picture);
            }
        }
        return out.toString();
    }

    private static int countDigits(String picture) {
        int n = 0;
        for (char c : picture.toCharArray()) {
            if (c == '9' || c == 'Z') {
                n++;
            }
        }
        return n;
    }
}
