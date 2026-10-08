package com.carddemo.online.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.online.config.CardDemoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** ONL-ACU-01: every COACTUPC 1200-EDIT-MAP-INPUTS rule, in the legacy evaluation order. */
class AccountUpdateValidatorTest {
    static final LocalDate TODAY = LocalDate.of(2022, 7, 6);
    static final AccountUpdateValidator V = validator(true);

    static AccountUpdateValidator validator(boolean blankPhoneQuirk) {
        return new AccountUpdateValidator(new CardDemoProperties("2022-07-06T10:00:00", null, null,
                new CardDemoProperties.Quirks(blankPhoneQuirk, true, true)), new ObjectMapper());
    }

    static Map<String, String> valid() {
        Map<String, String> m = new LinkedHashMap<>();
        String[][] kv = {
            {"activeStatus", "Y"}, {"openYear", "2014"}, {"openMonth", "11"}, {"openDay", "20"},
            {"creditLimit", "2020.00"}, {"expiryYear", "2025"}, {"expiryMonth", "05"}, {"expiryDay", "20"},
            {"cashCreditLimit", "1020.00"}, {"reissueYear", "2025"}, {"reissueMonth", "05"}, {"reissueDay", "20"},
            {"currentBalance", "1288.10"}, {"currentCycleCredit", "1164.87"}, {"currentCycleDebit", "-70.77"},
            {"groupId", "A000000000"}, {"ssn1", "020"}, {"ssn2", "97"}, {"ssn3", "3888"},
            {"dobYear", "1961"}, {"dobMonth", "06"}, {"dobDay", "08"}, {"ficoScore", "750"},
            {"firstName", "Immanuel"}, {"middleName", "Madeline"}, {"lastName", "Kessler"},
            {"addressLine1", "1 Main Street"}, {"addressLine2", ""}, {"city", "New York"}, {"state", "NY"},
            {"zip", "10001"}, {"country", "USA"}, {"phone1a", "212"}, {"phone1b", "555"}, {"phone1c", "1234"},
            {"phone2a", "212"}, {"phone2b", "555"}, {"phone2c", "9876"}, {"governmentId", ""},
            {"eftAccountId", "0053581756"}, {"primaryCardHolder", "Y"}};
        for (String[] e : kv) {
            m.put(e[0], e[1]);
        }
        return m;
    }

    static AccountFields fields(Map<String, String> m) {
        try {
            RecordComponent[] rc = AccountFields.class.getRecordComponents();
            Class<?>[] types = Arrays.stream(rc).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Object[] args = Arrays.stream(rc).map(c -> m.get(c.getName())).toArray();
            return AccountFields.class.getDeclaredConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static AccountFields with(String... kv) {
        Map<String, String> m = valid();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return fields(m);
    }

    @Test
    @DisplayName("ONL-ACU-01 COACTUPC 1200-EDIT-MAP-INPUTS: a fully valid screen passes")
    void validScreen() {
        assertThatCode(() -> V.validate(fields(valid()), TODAY)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "[{index}] {0}=''{1}'' -> {2}")
    @DisplayName("ONL-ACU-01 COACTUPC field edits")
    @CsvSource(delimiter = '|', value = {
        // 1220-EDIT-YESNO (account status)
        "activeStatus||Account Status must be supplied.",
        "activeStatus|X|Account Status must be Y or N.",
        "activeStatus|y|Account Status must be Y or N.",
        // EDIT-DATE-CCYYMMDD (open date)
        "openYear||Open Date : Year must be supplied.",
        "openYear|1850|Open Date : Century is not valid.",
        "openMonth|13|Open Date: Month must be a number between 1 and 12.",
        "openMonth||Open Date : Month must be supplied.",
        "openDay|32|Open Date:day must be a number between 1 and 31.",
        "openDay||Open Date : Day must be supplied.",
        // 1250-EDIT-SIGNED-9V2
        "creditLimit||Credit Limit must be supplied.",
        "creditLimit|abc|Credit Limit is not valid",
        "cashCreditLimit|1O0.00|Cash Credit Limit is not valid",
        "currentBalance|12x|Current Balance is not valid",
        "currentCycleCredit|--1|Current Cycle Credit Limit is not valid",
        "currentCycleDebit||Current Cycle Debit Limit must be supplied.",
        // expiry / reissue dates
        "expiryMonth|00|Expiry Date: Month must be a number between 1 and 12.",
        "reissueMonth|AB|Reissue Date: Month must be a number between 1 and 12.",
        // 1265-EDIT-US-SSN
        "ssn1||SSN: First 3 chars must be supplied.",
        "ssn1|12A|SSN: First 3 chars must be all numeric.",
        "ssn1|666|SSN: First 3 chars: should not be 000, 666, or between 900 and 999",
        "ssn1|901|SSN: First 3 chars: should not be 000, 666, or between 900 and 999",
        "ssn2|00|SSN 4th & 5th chars must not be zero.",
        "ssn3|12|SSN Last 4 chars must be all numeric.",
        // date of birth
        "dobDay|31|Date of Birth:Cannot have 31 days in this month.",
        // 1245-EDIT-NUM-REQD + 1275-EDIT-FICO-SCORE
        "ficoScore||FICO Score must be supplied.",
        "ficoScore|7X0|FICO Score must be all numeric.",
        "ficoScore|274|FICO Score: should be between 300 and 850",
        "ficoScore|851|FICO Score: should be between 300 and 850",
        // 1225 / 1235 alpha edits
        "firstName||First Name must be supplied.",
        "firstName|J0hn|First Name can have alphabets only.",
        "middleName|Q1|Middle Name can have alphabets only.",
        "lastName||Last Name must be supplied.",
        // 1215-EDIT-MANDATORY
        "addressLine1||Address Line 1 must be supplied.",
        // 1270-EDIT-US-STATE-CD
        "state|ZZ|State: is not a valid state code",
        "state|N1|State can have alphabets only.",
        // zip / city / country
        "zip||Zip must be supplied.",
        "zip|1000A|Zip must be all numeric.",
        "city||City must be supplied.",
        "country|U5A|Country can have alphabets only.",
        // 1260-EDIT-US-PHONE-NUM
        "phone1a|21A|Phone Number 1: Area code must be A 3 digit number.",
        "phone1a|000|Phone Number 1: Area code cannot be zero",
        "phone1a|999|Phone Number 1: Not valid North America general purpose area code",
        "phone1b|5X5|Phone Number 1: Prefix code must be A 3 digit number.",
        "phone1b|000|Phone Number 1: Prefix code cannot be zero",
        "phone2c|12|Phone Number 2: Line number code must be A 4 digit number.",
        "phone2c|0000|Phone Number 2: Line number code cannot be zero",
        // EFT + primary holder
        "eftAccountId||EFT Account Id must be supplied.",
        "eftAccountId|0000000000|EFT Account Id must not be zero.",
        "primaryCardHolder|X|Primary Card Holder must be Y or N."})
    void fieldEdit(String field, String value, String message) {
        assertThatThrownBy(() -> V.validate(with(field, value == null ? "" : value), TODAY)).hasMessage(message);
    }

    @Test
    @DisplayName("ONL-ACU-01 COACTUPC 1280-EDIT-US-STATE-ZIP-CD: state and zip prefix must match")
    void stateZip() {
        assertThatThrownBy(() -> V.validate(with("state", "CA", "zip", "10001"), TODAY))
                .hasMessage("Invalid zip code for state");
    }

    @Test
    @DisplayName("ONL-ACU-01 COACTUPC 1200-EDIT-MAP-INPUTS: first failing field wins (legacy order status -> open date)")
    void order() {
        assertThatThrownBy(() -> V.validate(with("activeStatus", "X", "openMonth", "13", "ficoScore", "1"), TODAY))
                .hasMessage("Account Status must be Y or N.");
        assertThatThrownBy(() -> V.validate(with("ficoScore", "100", "firstName", ""), TODAY))
                .hasMessage("FICO Score: should be between 300 and 850");
    }

    @Test
    @DisplayName("ONL-ACU-01 COACTUPC 1260-EDIT-US-PHONE-NUM QUIRK-ACU-01: fully blank phone is accepted")
    void blankPhoneQuirk() {
        assertThatCode(() -> V.validate(with("phone2a", "", "phone2b", "", "phone2c", ""), TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ONL-ACU-01 COACTUPC 1260-EDIT-US-PHONE-NUM: blank area code with prefix supplied")
    void blankArea() {
        assertThatThrownBy(() -> V.validate(with("phone1a", ""), TODAY))
                .hasMessage("Phone Number 1: Area code must be supplied.");
    }

    @Test
    @DisplayName("ONL-ACU-01 EDIT-DATE-OF-BIRTH: date of birth in the future is rejected")
    void dobFuture() {
        assertThatThrownBy(() -> V.validate(with("dobYear", "2023"), TODAY))
                .hasMessageContaining("Date of Birth");
    }

    @Test
    @DisplayName("ONL-ACU-01 EDIT-DAY-MONTH-YEAR: 29 Feb only in leap years")
    void leapYear() {
        assertThatThrownBy(() -> V.validate(with("openYear", "2015", "openMonth", "02", "openDay", "29"), TODAY))
                .hasMessage("Open Date:Not a leap year.Cannot have 29 days in this month.");
        assertThatCode(() -> V.validate(with("openYear", "2016", "openMonth", "02", "openDay", "29"), TODAY))
                .doesNotThrowAnyException();
    }
}
