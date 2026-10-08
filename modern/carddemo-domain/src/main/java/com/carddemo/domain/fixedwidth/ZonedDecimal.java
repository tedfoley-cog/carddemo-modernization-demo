package com.carddemo.domain.fixedwidth;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Codec for COBOL zoned-decimal (USAGE DISPLAY) numerics, e.g. {@code PIC S9(10)V99}.
 *
 * <p>Signed fields carry the sign in the last byte using the EBCDIC overpunch convention as it
 * appears after an EBCDIC-to-ASCII translation: {@code {ABCDEFGHI} = +0..+9} and
 * {@code }JKLMNOPQR} = -0..-9}. Unsigned fields are plain digits. Decoding follows GnuCOBOL's
 * lenient handling of unpopulated storage: spaces read as zero digits.
 */
public final class ZonedDecimal {

    private static final String POSITIVE = "{ABCDEFGHI";
    private static final String NEGATIVE = "}JKLMNOPQR";

    private ZonedDecimal() {
    }

    public static BigDecimal decode(CharSequence raw, int scale, boolean signed) {
        int length = raw.length();
        StringBuilder digits = new StringBuilder(length);
        boolean negative = false;
        for (int i = 0; i < length; i++) {
            char c = raw.charAt(i);
            boolean last = i == length - 1;
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c == ' ') {
                digits.append('0');
            } else if (last && POSITIVE.indexOf(c) >= 0) {
                digits.append((char) ('0' + POSITIVE.indexOf(c)));
            } else if (last && NEGATIVE.indexOf(c) >= 0) {
                digits.append((char) ('0' + NEGATIVE.indexOf(c)));
                negative = true;
            } else if (last && (c == '-' || c == '+')) {
                digits.append('0');
                negative = c == '-';
            } else {
                throw new FixedWidthException("Invalid zoned decimal '" + raw + "'");
            }
        }
        BigInteger unscaled = new BigInteger(digits.toString());
        BigDecimal value = new BigDecimal(negative && signed ? unscaled.negate() : unscaled, scale);
        return value;
    }

    /**
     * Encodes with COBOL MOVE semantics: the value is truncated (not rounded) to the field's
     * scale and high-order digits that do not fit are dropped.
     */
    public static String encode(BigDecimal value, int digits, int scale, boolean signed) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        BigDecimal truncated = CobolDecimal.truncate(v, digits - scale, scale);
        boolean negative = signed && truncated.signum() < 0;
        String abs = truncated.abs().unscaledValue().toString();
        if (abs.length() < digits) {
            abs = "0".repeat(digits - abs.length()) + abs;
        }
        if (!signed) {
            return abs;
        }
        int lastDigit = abs.charAt(digits - 1) - '0';
        char overpunch = (negative ? NEGATIVE : POSITIVE).charAt(lastDigit);
        return abs.substring(0, digits - 1) + overpunch;
    }
}
