package com.carddemo.domain.fixedwidth;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Fixed-point arithmetic with COBOL receiving-field semantics: results are truncated toward
 * zero to the receiving scale and high-order digits beyond the PICTURE are lost (no ON SIZE
 * ERROR clause is coded anywhere in the batch stream).
 */
public final class CobolDecimal {

    private CobolDecimal() {
    }

    public static BigDecimal truncate(BigDecimal value, int integerDigits, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.DOWN);
        BigInteger modulus = BigInteger.TEN.pow(integerDigits + scale);
        BigInteger unscaled = scaled.unscaledValue();
        BigInteger kept = unscaled.abs().mod(modulus);
        return new BigDecimal(unscaled.signum() < 0 ? kept.negate() : kept, scale);
    }

    /** {@code PIC S9(9)V99}: transaction amounts, category balances, interest, report totals. */
    public static BigDecimal s9v2(BigDecimal value) {
        return truncate(value, 9, 2);
    }

    /** {@code PIC S9(10)V99}: account balances, limits and cycle buckets. */
    public static BigDecimal s10v2(BigDecimal value) {
        return truncate(value, 10, 2);
    }
}
