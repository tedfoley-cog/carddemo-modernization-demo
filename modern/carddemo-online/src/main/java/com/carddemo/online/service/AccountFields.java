package com.carddemo.online.service;

import com.carddemo.online.domain.Account;
import com.carddemo.online.domain.Customer;
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
        String ssn = String.format("%09d", c.getSsn());
        return new AccountFields(
                a.getActiveStatus(),
                part(a.getOpenDate(), 0, 4), part(a.getOpenDate(), 5, 7), part(a.getOpenDate(), 8, 10),
                LegacyFormat.currency(a.getCreditLimit()),
                part(a.getExpirationDate(), 0, 4), part(a.getExpirationDate(), 5, 7), part(a.getExpirationDate(), 8, 10),
                LegacyFormat.currency(a.getCashCreditLimit()),
                part(a.getReissueDate(), 0, 4), part(a.getReissueDate(), 5, 7), part(a.getReissueDate(), 8, 10),
                LegacyFormat.currency(a.getCurrBal()), LegacyFormat.currency(a.getCurrCycCredit()),
                LegacyFormat.currency(a.getCurrCycDebit()),
                a.getGroupId(),
                ssn.substring(0, 3), ssn.substring(3, 5), ssn.substring(5),
                part(c.getDob(), 0, 4), part(c.getDob(), 5, 7), part(c.getDob(), 8, 10),
                LegacyFormat.zeroPad(c.getFicoScore(), 3),
                c.getFirstName(), c.getMiddleName(), c.getLastName(),
                c.getAddrLine1(), c.getAddrLine2(), c.getAddrLine3(), c.getStateCd(), c.getZip(), c.getCountryCd(),
                part(c.getPhone1(), 1, 4), part(c.getPhone1(), 5, 8), part(c.getPhone1(), 9, 13),
                part(c.getPhone2(), 1, 4), part(c.getPhone2(), 5, 8), part(c.getPhone2(), 9, 13),
                c.getGovtIssuedId(), c.getEftAccountId(), c.getPriCardHolderInd());
    }

    static String part(String s, int from, int to) {
        if (s == null || s.length() <= from) {
            return "";
        }
        return s.substring(from, Math.min(to, s.length())).trim();
    }
}
