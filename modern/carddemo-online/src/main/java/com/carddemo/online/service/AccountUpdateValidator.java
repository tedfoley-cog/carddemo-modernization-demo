package com.carddemo.online.service;

import com.carddemo.online.config.CardDemoProperties;
import com.carddemo.online.legacy.Numval;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * COACTUPC 1200-EDIT-MAP-INPUTS and its edit paragraphs (1215-1280 plus the CSUTLDPY date edits).
 * The COBOL keeps the first message only (WS-RETURN-MSG-OFF guard), so the first failing edit wins
 * and is raised as a {@link LegacyRuleException} naming the paragraph.
 */
@Component
public class AccountUpdateValidator {
    private static final String P = "COACTUPC ";
    private final Set<String> areaCodes = new HashSet<>();
    private final Set<String> stateCodes = new HashSet<>();
    private final Set<String> stateZip = new HashSet<>();
    private final CardDemoProperties props;

    public AccountUpdateValidator(CardDemoProperties props, ObjectMapper mapper) {
        this.props = props;
        try (InputStream in = getClass().getResourceAsStream("/legacy/cslkpcdy.json")) {
            JsonNode n = mapper.readTree(in);
            n.get("validGeneralPurposeAreaCodes").forEach(v -> areaCodes.add(v.asText()));
            n.get("validUsStateCodes").forEach(v -> stateCodes.add(v.asText()));
            n.get("validUsStateZipCd2Combos").forEach(v -> stateZip.add(v.asText()));
        } catch (IOException e) {
            throw new UncheckedIOException("CSLKPCDY lookup tables missing", e);
        }
    }

    /** COACTUPC 1200-EDIT-MAP-INPUTS, in the COBOL order. */
    public void validate(AccountFields f, LocalDate today) {
        editYesNo("Account Status", f.activeStatus(), "activeStatus");
        editDate("Open Date", f.openYear(), f.openMonth(), f.openDay(), "openYear");
        editSigned9v2("Credit Limit", f.creditLimit(), "creditLimit");
        editDate("Expiry Date", f.expiryYear(), f.expiryMonth(), f.expiryDay(), "expiryYear");
        editSigned9v2("Cash Credit Limit", f.cashCreditLimit(), "cashCreditLimit");
        editDate("Reissue Date", f.reissueYear(), f.reissueMonth(), f.reissueDay(), "reissueYear");
        editSigned9v2("Current Balance", f.currentBalance(), "currentBalance");
        editSigned9v2("Current Cycle Credit Limit", f.currentCycleCredit(), "currentCycleCredit");
        editSigned9v2("Current Cycle Debit Limit", f.currentCycleDebit(), "currentCycleDebit");
        editSsn(f);
        editDate("Date of Birth", f.dobYear(), f.dobMonth(), f.dobDay(), "dobYear");
        editDateOfBirth(f, today);
        editNumReqd("FICO Score", f.ficoScore(), 3, "ficoScore");
        editFico(f.ficoScore());
        editAlphaReqd("First Name", f.firstName(), "firstName");
        editAlphaOpt("Middle Name", f.middleName(), "middleName");
        editAlphaReqd("Last Name", f.lastName(), "lastName");
        editMandatory("Address Line 1", f.addressLine1(), "addressLine1");
        editAlphaReqd("State", f.state(), "state");
        editStateCode(f.state());
        editNumReqd("Zip", f.zip(), 5, "zip");
        editAlphaReqd("City", f.city(), "city");
        editAlphaReqd("Country", f.country(), "country");
        editPhone("Phone Number 1", f.phone1a(), f.phone1b(), f.phone1c(), "phone1a");
        editPhone("Phone Number 2", f.phone2a(), f.phone2b(), f.phone2c(), "phone2a");
        editNumReqd("EFT Account Id", f.eftAccountId(), 10, "eftAccountId");
        editYesNo("Primary Card Holder", f.primaryCardHolder(), "primaryCardHolder");
        editStateZip(f.state(), f.zip());
    }

    /** 1100-RECEIVE-MAP: '*' or spaces are treated as LOW-VALUES (not entered). */
    static boolean blank(String s) {
        return s == null || s.isBlank() || "*".equals(s.trim());
    }

    private static LegacyRuleException err(String msg, String field, String para) {
        return new LegacyRuleException(msg, field, P + para);
    }

    void editMandatory(String name, String v, String field) {
        if (blank(v)) {
            throw err(name + " must be supplied.", field, "1215-EDIT-MANDATORY");
        }
    }

    void editYesNo(String name, String v, String field) {
        if (blank(v) || "0".equals(v.trim())) {
            throw err(name + " must be supplied.", field, "1220-EDIT-YESNO");
        }
        if (!v.equals("Y") && !v.equals("N")) {
            throw err(name + " must be Y or N.", field, "1220-EDIT-YESNO");
        }
    }

    void editAlphaReqd(String name, String v, String field) {
        if (blank(v)) {
            throw err(name + " must be supplied.", field, "1225-EDIT-ALPHA-REQD");
        }
        if (!v.matches("[A-Za-z ]*")) {
            throw err(name + " can have alphabets only.", field, "1225-EDIT-ALPHA-REQD");
        }
    }

    void editAlphaOpt(String name, String v, String field) {
        if (!blank(v) && !v.matches("[A-Za-z ]*")) {
            throw err(name + " can have alphabets only.", field, "1235-EDIT-ALPHA-OPT");
        }
    }

    /** 1245-EDIT-NUM-REQD: only the first {@code len} characters are edited (space padded). */
    void editNumReqd(String name, String v, int len, String field) {
        if (blank(v)) {
            throw err(name + " must be supplied.", field, "1245-EDIT-NUM-REQD");
        }
        String head = (v + " ".repeat(len)).substring(0, len);
        if (!head.chars().allMatch(Character::isDigit)) {
            throw err(name + " must be all numeric.", field, "1245-EDIT-NUM-REQD");
        }
        if (Long.parseLong(head) == 0) {
            throw err(name + " must not be zero.", field, "1245-EDIT-NUM-REQD");
        }
    }

    void editSigned9v2(String name, String v, String field) {
        if (blank(v)) {
            throw err(name + " must be supplied.", field, "1250-EDIT-SIGNED-9V2");
        }
        if (!Numval.isValid(v)) {
            throw err(name + " is not valid", field, "1250-EDIT-SIGNED-9V2");
        }
    }

    /** 1260-EDIT-US-PHONE-NUM. */
    void editPhone(String name, String a, String b, String c, String field) {
        boolean legacyBlankTest = props.quirks().blankPhoneAccepted();
        // Legacy tests NUMA twice and never NUMC: area + prefix blank accepts the phone (QUIRK-ACU-02)
        if (legacyBlankTest ? (blank(a) && blank(b)) : (blank(a) && blank(b) && blank(c))) {
            return;
        }
        String para = "1260-EDIT-US-PHONE-NUM";
        String fieldB = field.replace('a', 'b');
        String fieldC = field.replace('a', 'c');
        if (blank(a)) {
            throw err(name + ": Area code must be supplied.", field, para);
        }
        String at = a.trim();
        if (!(at.length() == 3 && at.chars().allMatch(Character::isDigit))) {
            throw err(name + ": Area code must be A 3 digit number.", field, para);
        }
        if (Integer.parseInt(at) == 0) {
            throw err(name + ": Area code cannot be zero", field, para);
        }
        if (!areaCodes.contains(at)) {
            throw err(name + ": Not valid North America general purpose area code", field, para);
        }
        if (blank(b)) {
            throw err(name + ": Prefix code must be supplied.", fieldB, para);
        }
        if (!(b.length() == 3 && b.chars().allMatch(Character::isDigit))) {
            throw err(name + ": Prefix code must be A 3 digit number.", fieldB, para);
        }
        if (Integer.parseInt(b) == 0) {
            throw err(name + ": Prefix code cannot be zero", fieldB, para);
        }
        if (blank(c)) {
            throw err(name + ": Line number code must be supplied.", fieldC, para);
        }
        if (!(c.length() == 4 && c.chars().allMatch(Character::isDigit))) {
            throw err(name + ": Line number code must be A 4 digit number.", fieldC, para);
        }
        if (Integer.parseInt(c) == 0) {
            throw err(name + ": Line number code cannot be zero", fieldC, para);
        }
    }

    /** 1265-EDIT-US-SSN. */
    void editSsn(AccountFields f) {
        editNumReqd("SSN: First 3 chars", f.ssn1(), 3, "ssn1");
        int first = Integer.parseInt((f.ssn1() + "   ").substring(0, 3));
        if (first == 0 || first == 666 || first >= 900) {
            throw err("SSN: First 3 chars: should not be 000, 666, or between 900 and 999", "ssn1",
                    "1265-EDIT-US-SSN");
        }
        editNumReqd("SSN 4th & 5th chars", f.ssn2(), 2, "ssn2");
        editNumReqd("SSN Last 4 chars", f.ssn3(), 4, "ssn3");
    }

    void editStateCode(String state) {
        if (!stateCodes.contains(state.trim().toUpperCase())) {
            throw err("State: is not a valid state code", "state", "1270-EDIT-US-STATE-CD");
        }
    }

    void editFico(String fico) {
        int v = Integer.parseInt(fico.trim().substring(0, 3));
        if (v < 300 || v > 850) {
            throw err("FICO Score: should be between 300 and 850", "ficoScore", "1275-EDIT-FICO-SCORE");
        }
    }

    void editStateZip(String state, String zip) {
        if (!stateZip.contains(state.trim().toUpperCase() + zip.trim().substring(0, 2))) {
            throw err("Invalid zip code for state", "zip", "1280-EDIT-US-STATE-ZIP-CD");
        }
    }

    /** CSUTLDPY EDIT-DATE-CCYYMMDD (year, month, day, then day-of-month and leap-year rules). */
    void editDate(String name, String y, String m, String d, String field) {
        String fm = field.replace("Year", "Month");
        String fd = field.replace("Year", "Day");
        String para = "EDIT-DATE-CCYYMMDD";
        if (blank(y)) {
            throw err(name + " : Year must be supplied.", field, para + " (EDIT-YEAR-CCYY)");
        }
        if (!(y.trim().length() == 4 && y.trim().chars().allMatch(Character::isDigit))) {
            throw err(name + " must be 4 digit number.", field, para + " (EDIT-YEAR-CCYY)");
        }
        int year = Integer.parseInt(y.trim());
        if (year / 100 != 19 && year / 100 != 20) {
            throw err(name + " : Century is not valid.", field, para + " (EDIT-YEAR-CCYY)");
        }
        if (blank(m)) {
            throw err(name + " : Month must be supplied.", fm, para + " (EDIT-MONTH)");
        }
        int month = toInt(m);
        if (month < 1 || month > 12) {
            throw err(name + ": Month must be a number between 1 and 12.", fm, para + " (EDIT-MONTH)");
        }
        if (blank(d)) {
            throw err(name + " : Day must be supplied.", fd, para + " (EDIT-DAY)");
        }
        int day = toInt(d);
        if (day < 1 || day > 31) {
            throw err(name + ":day must be a number between 1 and 31.", fd, para + " (EDIT-DAY)");
        }
        if (day == 31 && (month == 4 || month == 6 || month == 9 || month == 11 || month == 2)) {
            throw err(name + ":Cannot have 31 days in this month.", fd, para + " (EDIT-DAY-MONTH-YEAR)");
        }
        if (month == 2 && day == 30) {
            throw err(name + ":Cannot have 30 days in this month.", fd, para + " (EDIT-DAY-MONTH-YEAR)");
        }
        if (month == 2 && day == 29) {
            boolean leap = year % 100 == 0 ? year % 400 == 0 : year % 4 == 0;
            if (!leap) {
                throw err(name + ":Not a leap year.Cannot have 29 days in this month.", fd,
                        para + " (EDIT-DAY-MONTH-YEAR)");
            }
        }
    }

    /** CSUTLDPY EDIT-DATE-OF-BIRTH: business date must be strictly after the DOB. */
    void editDateOfBirth(AccountFields f, LocalDate today) {
        LocalDate dob = LocalDate.of(Integer.parseInt(f.dobYear().trim()), toInt(f.dobMonth()), toInt(f.dobDay()));
        if (!today.isAfter(dob)) {
            throw err("Date of Birth:cannot be in the future ", "dobYear", "EDIT-DATE-OF-BIRTH");
        }
    }

    private static int toInt(String s) {
        String t = s.trim();
        return t.isEmpty() || t.length() > 2 || !t.chars().allMatch(Character::isDigit) ? -1 : Integer.parseInt(t);
    }
}
