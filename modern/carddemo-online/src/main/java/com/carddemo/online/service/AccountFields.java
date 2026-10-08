package com.carddemo.online.service;

import com.carddemo.online.legacy.LegacyDates;
import com.carddemo.domain.model.Account;
import com.carddemo.domain.model.Customer;
import com.carddemo.online.legacy.LegacyFormat;

/**
 * Editable fields of the COACTUP map (CACTUPAI), one component per map input field. Dates, SSN and
 * phones stay split exactly as on the 3270 screen so the same per-part edits apply.
 */
public record AccountFields(
        String activeStatus,
        String openYear, String openMonth, String openDay,
        String creditLimit,
        String expiryYear, String expiryMonth, String expiryDay,
        String cashCreditLimit,
        String reissueYear, String reissueMonth, String reissueDay,
        String currentBalance, String currentCycleCredit, String currentCycleDebit,
        String groupId,
        String ssn1, String ssn2, String ssn3,
        String dobYear, String dobMonth, String dobDay,
        String ficoScore,
        String firstName, String middleName, String lastName,
        String addressLine1, String addressLine2, String city, String state, String zip, String country,
        String phone1a, String phone1b, String phone1c,
        String phone2a, String phone2b, String phone2c,
        String governmentId, String eftAccountId, String primaryCardHolder) {

    /** COACTUPC 3202-SHOW-ORIGINAL-VALUES. */
    public static AccountFields of(Account a, Customer c) {
        String ssn = (c.getSsn() == null ? "" : c.getSsn());
        return new AccountFields(
                a.getActiveStatus(),
                part(LegacyDates.text(a.getOpenDate()), 0, 4), part(LegacyDates.text(a.getOpenDate()), 5, 7), part(LegacyDates.text(a.getOpenDate()), 8, 10),
                LegacyFormat.currency(a.getCreditLimit()),
                part(LegacyDates.text(a.getExpirationDate()), 0, 4), part(LegacyDates.text(a.getExpirationDate()), 5, 7), part(LegacyDates.text(a.getExpirationDate()), 8, 10),
                LegacyFormat.currency(a.getCashCreditLimit()),
                part(LegacyDates.text(a.getReissueDate()), 0, 4), part(LegacyDates.text(a.getReissueDate()), 5, 7), part(LegacyDates.text(a.getReissueDate()), 8, 10),
                LegacyFormat.currency(a.getCurrentBalance()), LegacyFormat.currency(a.getCurrentCycleCredit()),
                LegacyFormat.currency(a.getCurrentCycleDebit()),
                a.getGroupId(),
                ssn.substring(0, 3), ssn.substring(3, 5), ssn.substring(5),
                part(LegacyDates.text(c.getDateOfBirth()), 0, 4), part(LegacyDates.text(c.getDateOfBirth()), 5, 7), part(LegacyDates.text(c.getDateOfBirth()), 8, 10),
                LegacyFormat.zeroPad(c.getFicoCreditScore(), 3),
                c.getFirstName(), c.getMiddleName(), c.getLastName(),
                c.getAddressLine1(), c.getAddressLine2(), c.getAddressLine3(), c.getStateCode(), c.getZip(), c.getCountryCode(),
                part(c.getPhoneNumber1(), 1, 4), part(c.getPhoneNumber1(), 5, 8), part(c.getPhoneNumber1(), 9, 13),
                part(c.getPhoneNumber2(), 1, 4), part(c.getPhoneNumber2(), 5, 8), part(c.getPhoneNumber2(), 9, 13),
                c.getGovernmentIssuedId(), c.getEftAccountId(), c.getPrimaryCardHolder());
    }

    static String part(String s, int from, int to) {
        if (s == null || s.length() <= from) {
            return "";
        }
        return s.substring(from, Math.min(to, s.length())).trim();
    }
}
