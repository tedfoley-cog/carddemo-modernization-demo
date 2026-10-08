package com.carddemo.online.legacy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Reproduces the COBOL PICTURE edit masks used on the BMS maps, so API values compare 1:1. */
public final class LegacyFormat {
    private LegacyFormat() {
    }

    /** PIC +ZZZ,ZZZ,ZZZ.99 (COACTVWC / COACTUPC currency fields), e.g. "+      2,020.00". */
    public static String currency(BigDecimal value) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String body = f.format(value.abs().setScale(2, RoundingMode.DOWN));
        String sign = value.signum() < 0 ? "-" : "+";
        return sign + " ".repeat(Math.max(0, 14 - body.length())) + body;
    }

    /** PIC +99999999.99 (COTRN00C/01C/02C amounts), e.g. "-00000012.50". */
    public static String signedAmount(BigDecimal value, int intDigits) {
        BigDecimal v = value.setScale(2, RoundingMode.DOWN);
        String body = v.abs().toPlainString();
        int dot = body.indexOf('.');
        String intPart = body.substring(0, dot);
        if (intPart.length() > intDigits) {
            intPart = intPart.substring(intPart.length() - intDigits);
        }
        return (v.signum() < 0 ? "-" : "+") + "0".repeat(intDigits - intPart.length()) + intPart + body.substring(dot);
    }

    /** CUST-SSN 9(09) shown as 999-99-9999 (COACTVWC 1200-SETUP-SCREEN-VARS). */
    public static String ssn(long ssn) {
        String s = String.format("%09d", ssn);
        return s.substring(0, 3) + "-" + s.substring(3, 5) + "-" + s.substring(5);
    }

    public static String zeroPad(long value, int width) {
        return String.format("%0" + width + "d", value);
    }

    /** "YYYY-MM-DD..." timestamp to the MM/DD/YY list format of COTRN00C POPULATE-TRAN-DATA. */
    public static String mmddyy(String ts) {
        if (ts == null || ts.length() < 10) {
            return "";
        }
        return ts.substring(5, 7) + "/" + ts.substring(8, 10) + "/" + ts.substring(2, 4);
    }
}
