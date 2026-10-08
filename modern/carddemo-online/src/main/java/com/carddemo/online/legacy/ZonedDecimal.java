package com.carddemo.online.legacy;

import java.math.BigDecimal;

/**
 * Decodes COBOL zoned-decimal (USAGE DISPLAY, SIGN TRAILING overpunch) fields as written by the
 * GnuCOBOL runtime with {@code -fsign=EBCDIC}: '{'/A-I = +0..9 and '}'/J-R = -0..9 in the last byte.
 */
public final class ZonedDecimal {
    private ZonedDecimal() {
    }

    public static BigDecimal decode(String raw, int scale) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        char last = s.charAt(s.length() - 1);
        boolean negative = false;
        char digit;
        if (last >= '0' && last <= '9') {
            digit = last;
        } else if (last == '{') {
            digit = '0';
        } else if (last >= 'A' && last <= 'I') {
            digit = (char) ('1' + (last - 'A'));
        } else if (last == '}') {
            digit = '0';
            negative = true;
        } else if (last >= 'J' && last <= 'R') {
            digit = (char) ('1' + (last - 'J'));
            negative = true;
        } else if (last == 'p' || (last >= 'q' && last <= 'y')) {
            digit = (char) ('0' + (last - 'p'));
            negative = true;
        } else {
            throw new IllegalArgumentException("Not a zoned decimal: '" + raw + "'");
        }
        String digits = s.substring(0, s.length() - 1) + digit;
        if (digits.startsWith("+") || digits.startsWith("-")) {
            negative = digits.startsWith("-");
            digits = digits.substring(1);
        }
        BigDecimal value = new BigDecimal(digits).movePointLeft(scale);
        return negative ? value.negate() : value;
    }

    public static String encode(BigDecimal value, int digits, int scale) {
        BigDecimal scaled = value.setScale(scale, java.math.RoundingMode.DOWN);
        String body = scaled.abs().unscaledValue().toString();
        if (body.length() > digits) {
            body = body.substring(body.length() - digits);
        }
        body = "0".repeat(digits - body.length()) + body;
        int lastDigit = body.charAt(digits - 1) - '0';
        char over = scaled.signum() < 0
                ? (lastDigit == 0 ? '}' : (char) ('J' + lastDigit - 1))
                : (lastDigit == 0 ? '{' : (char) ('A' + lastDigit - 1));
        return body.substring(0, digits - 1) + over;
    }
}
