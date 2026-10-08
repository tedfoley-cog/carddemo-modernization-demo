package com.carddemo.online.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** ONL-TRA-01: COTRN02C VALIDATE-INPUT-KEY-FIELDS / VALIDATE-INPUT-DATA-FIELDS. */
class TransactionAddRulesTest {
    private final TransactionAddService svc = mock(TransactionAddService.class, CALLS_REAL_METHODS);

    static TransactionAddService.AddRequest req(String field, String value) {
        String[] v = {"00000000001", "", "01", "0001", "POS TERM", "Coffee", "+00000012.50", "2022-07-06",
            "2022-07-06", "800000000", "Cafe", "Austin", "73301", "Y"};
        String[] names = {"accountId", "cardNumber", "typeCd", "categoryCd", "source", "description", "amount",
            "origDate", "procDate", "merchantId", "merchantName", "merchantCity", "merchantZip", "confirm"};
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(field)) {
                v[i] = value == null ? "" : value;
            }
        }
        return new TransactionAddService.AddRequest(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9],
                v[10], v[11], v[12], v[13]);
    }

    @Test
    @DisplayName("ONL-TRA-01 COTRN02C VALIDATE-INPUT-KEY-FIELDS: account or card required")
    void keyRequired() {
        var r = new TransactionAddService.AddRequest("", "", "01", "0001", "S", "D", "+00000001.00", "2022-07-06",
                "2022-07-06", "1", "M", "C", "Z", "Y");
        assertThatThrownBy(() -> svc.validateKeyFields(r)).hasMessage("Account or Card Number must be entered...");
    }

    @Test
    @DisplayName("ONL-TRA-01 COTRN02C VALIDATE-INPUT-KEY-FIELDS: non-numeric account / card")
    void keyNumeric() {
        assertThatThrownBy(() -> svc.validateKeyFields(req("accountId", "12AB"))).hasMessage("Account ID must be Numeric...");
        var card = new TransactionAddService.AddRequest("", "4859X", "01", "0001", "S", "D", "+00000001.00",
                "2022-07-06", "2022-07-06", "1", "M", "C", "Z", "Y");
        assertThatThrownBy(() -> svc.validateKeyFields(card)).hasMessage("Card Number must be Numeric...");
    }

    @ParameterizedTest(name = "[{index}] {0}=''{1}'' -> {2}")
    @DisplayName("ONL-TRA-01 COTRN02C VALIDATE-INPUT-DATA-FIELDS")
    @CsvSource(delimiter = '|', value = {
        "typeCd||Type CD can NOT be empty...",
        "categoryCd||Category CD can NOT be empty...",
        "source||Source can NOT be empty...",
        "description||Description can NOT be empty...",
        "amount||Amount can NOT be empty...",
        "origDate||Orig Date can NOT be empty...",
        "procDate||Proc Date can NOT be empty...",
        "merchantId||Merchant ID can NOT be empty...",
        "merchantName||Merchant Name can NOT be empty...",
        "merchantCity||Merchant City can NOT be empty...",
        "merchantZip||Merchant Zip can NOT be empty...",
        "typeCd|0A|Type CD must be Numeric...",
        "categoryCd|00X1|Category CD must be Numeric...",
        "amount|12.50|Amount should be in format -99999999.99",
        "amount|abc|Amount should be in format -99999999.99",
        "origDate|07/06/2022|Orig Date should be in format YYYY-MM-DD",
        "procDate|20220706|Proc Date should be in format YYYY-MM-DD",
        "origDate|2022-02-30|Orig Date - Not a valid date...",
        "procDate|2022-13-01|Proc Date - Not a valid date...",
        "merchantId|80000A|Merchant ID must be Numeric..."})
    void dataEdits(String field, String value, String message) {
        assertThatThrownBy(() -> svc.validateDataFields(req(field, value))).hasMessage(message);
    }

    @Test
    @DisplayName("ONL-TRA-01 COTRN02C VALIDATE-INPUT-DATA-FIELDS: valid input passes; QUIRK-TRN-02 pre-1601 dates accepted")
    void validAndQuirk() {
        assertThatCode(() -> svc.validateDataFields(req("amount", "-00000012.50"))).doesNotThrowAnyException();
        assertThat(TransactionAddService.validDate("1500-02-30")).isTrue();
        assertThat(TransactionAddService.validDate("2024-02-29")).isTrue();
        assertThat(TransactionAddService.validDate("2023-02-29")).isFalse();
    }
}
