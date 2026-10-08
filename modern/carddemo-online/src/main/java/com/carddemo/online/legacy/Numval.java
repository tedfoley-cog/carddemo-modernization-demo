package com.carddemo.online.legacy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Behaviour of the COBOL intrinsic functions TEST-NUMVAL-C / NUMVAL-C used by COACTUPC and
 * COTRN02C: optional leading or trailing sign (+, -, CR, DB), optional currency sign, thousands
 * separators and one decimal point, surrounded by optional spaces.
 */
public final class Numval {
    private static final Pattern NUMVAL_C = Pattern.compile(
            "^\\s*(?<lsign>[+-])?\\s*\\$?\\s*(?<num>(\\d{1,3}(,\\d{3})+|\\d+)?(\\.\\d*)?)\\s*(?<tsign>[+-]|CR|DB|cr|db)?\\s*$");

    private Numval() {
    }

    public static boolean isValid(String text) {
        return parse(text).isPresent();
    }

    public static Optional<BigDecimal> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = NUMVAL_C.matcher(text);
        if (!m.matches()) {
            return Optional.empty();
        }
        String num = m.group("num");
        if (num == null || num.isEmpty() || num.equals(".")) {
            return Optional.empty();
        }
        String lsign = m.group("lsign");
        String tsign = m.group("tsign");
        if (lsign != null && tsign != null) {
            return Optional.empty();
        }
        BigDecimal value = new BigDecimal(num.replace(",", "").replaceAll("\\.$", ""));
        boolean negative = "-".equals(lsign) || (tsign != null && !tsign.equals("+"));
        return Optional.of(negative ? value.negate() : value);
    }
}
