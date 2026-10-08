package com.carddemo.online.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CardServiceRulesTest {
    private final CardService svc = new CardService(null);
    private static final CardService.CardChanges ORIG = new CardService.CardChanges("JOHN DOE", "Y", "05", "2025");

    @ParameterizedTest(name = "[{index}] acct=''{0}'' card=''{1}'' -> {2}")
    @DisplayName("COCRDSLC 2200-EDIT-MAP-INPUTS / 2210-EDIT-ACCOUNT / 2220-EDIT-CARD")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
        "NULL|NULL|No input received",
        "''|''|No input received",
        "00000000000|0000000000000000|No input received",
        "''|4859452612877065|Account number not provided",
        "1|NULL|ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER",
        "0000000000A|4859452612877065|ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER",
        "00000000001|''|Card number not provided",
        "00000000001|0000000000000000|Card number not provided",
        "00000000001|12|CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"})
    void keyEdits(String acct, String card, String message) {
        assertThatThrownBy(() -> CardService.keys(acct, card, "COCRDSLC")).hasMessage(message);
    }

    @Test
    @DisplayName("COCRDSLC 2210/2220: valid keys return the card number (account is not cross-checked - QUIRK-CRD-02)")
    void validKeys() {
        assertThat(CardService.keys("00000000099", "4859452612877065", "COCRDSLC")).isEqualTo("4859452612877065");
    }

    @Test
    @DisplayName("COCRDLIC 2250-EDIT-ARRAY: more than one selection")
    void multipleSelections() {
        assertThatThrownBy(() -> CardService.editSelections(List.of("S", "", "U")))
                .hasMessage("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE");
    }

    @Test
    @DisplayName("COCRDLIC 2250-EDIT-ARRAY: invalid action code")
    void invalidAction() {
        assertThatThrownBy(() -> CardService.editSelections(List.of("", "X"))).hasMessage("INVALID ACTION CODE");
        assertThatCode(() -> CardService.editSelections(List.of("", "U", ""))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "[{index}] {0}/{1}/{2}/{3} -> {4}")
    @DisplayName("COCRDUPC 1230-EDIT-NAME / 1240-EDIT-CARDSTATUS / 1250-EDIT-EXPIRY-MON / 1260-EDIT-EXPIRY-YEAR")
    @CsvSource(delimiter = '|', value = {
        "''|Y|05|2025|Card name not provided",
        "JOHN 2|Y|05|2025|Card name can only contain alphabets and spaces",
        "JANE DOE|''|05|2025|Card Active Status must be Y or N",
        "JANE DOE|X|05|2025|Card Active Status must be Y or N",
        "JANE DOE|y|05|2025|Card Active Status must be Y or N",
        "JANE DOE|Y|13|2025|Card expiry month must be between 1 and 12",
        "JANE DOE|Y|00|2025|Card expiry month must be between 1 and 12",
        "JANE DOE|Y|AB|2025|Card expiry month must be between 1 and 12",
        "JANE DOE|Y|05|1949|Invalid card expiry year",
        "JANE DOE|Y|05|2100|Invalid card expiry year"})
    void cardFieldEdits(String name, String status, String month, String year, String message) {
        var req = new CardService.CardUpdateRequest(ORIG, new CardService.CardChanges(name, status, month, year));
        assertThatThrownBy(() -> svc.editChanges(req)).hasMessage(message);
    }

    @Test
    @DisplayName("COCRDUPC 1200-EDIT-MAP-INPUTS: no change detected (name compared upper-case, month numerically)")
    void noChange() {
        var req = new CardService.CardUpdateRequest(ORIG, new CardService.CardChanges("john doe", "Y", "5", "2025"));
        assertThatThrownBy(() -> svc.editChanges(req)).hasMessage("No change detected with respect to values fetched.");
    }

    @Test
    @DisplayName("COCRDUPC 1200-EDIT-MAP-INPUTS: valid change passes all edits")
    void validChange() {
        var req = new CardService.CardUpdateRequest(ORIG, new CardService.CardChanges("JANE DOE", "N", "12", "2030"));
        assertThatCode(() -> svc.editChanges(req)).doesNotThrowAnyException();
        assertThat(svc.validate("00000000001", "4859452612877065", req).message())
                .isEqualTo("Changes validated.Press F5 to save");
    }
}
