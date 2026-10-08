package com.carddemo.online.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LegacyFormatTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("Zoned decimal trailing overpunch (PIC S9(n)V99 DISPLAY) as written by the legacy loader")
    @CsvSource({"00000012881{,1288.10", "00000000707P,-70.77", "00000000707},-70.70", "00000020200{,2020.00", "00000000050J,-5.01"})
    void zonedDecode(String raw, String expected) {
        assertThat(ZonedDecimal.decode(raw, 2)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("Zoned decimal round-trips")
    void zonedRoundTrip() {
        BigDecimal v = new BigDecimal("-919.00");
        assertThat(ZonedDecimal.decode(ZonedDecimal.encode(v, 12, 2), 2)).isEqualByComparingTo(v);
    }

    @Test
    @DisplayName("COACTVWC edited currency +ZZZ,ZZZ,ZZZ.99 and COTRN00C/COBIL00C +9(n).99 pictures")
    void pictures() {
        assertThat(LegacyFormat.currency(new BigDecimal("1288.10"))).isEqualTo("+      1,288.10");
        assertThat(LegacyFormat.currency(new BigDecimal("-70.77"))).isEqualTo("-         70.77");
        assertThat(LegacyFormat.signedAmount(new BigDecimal("504.77"), 8)).isEqualTo("+00000504.77");
        assertThat(LegacyFormat.signedAmount(new BigDecimal("-919.00"), 8)).isEqualTo("-00000919.00");
        assertThat(LegacyFormat.signedAmount(new BigDecimal("1288.10"), 10)).isEqualTo("+0000001288.10");
    }

    @Test
    @DisplayName("COTRN00C TDATE mm/dd/yy and zero-padded keys")
    void dates() {
        assertThat(LegacyFormat.mmddyy("2022-06-10 19:27:53.000000")).isEqualTo("06/10/22");
        assertThat(LegacyFormat.zeroPad(1, 11)).isEqualTo("00000000001");
        assertThat(LegacyFormat.ssn(20973888L)).isEqualTo("020-97-3888");
    }
}
