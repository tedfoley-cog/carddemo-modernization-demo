package com.carddemo.online.service;

import com.carddemo.online.legacy.LegacyDates;
import com.carddemo.online.config.BusinessClock;
import com.carddemo.domain.model.Account;
import com.carddemo.domain.model.CardXref;
import com.carddemo.domain.model.Customer;
import com.carddemo.online.legacy.LegacyFormat;
import com.carddemo.online.legacy.Numval;
import com.carddemo.online.repo.AccountRepository;
import com.carddemo.online.repo.CardXrefRepository;
import com.carddemo.online.repo.CustomerRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COACTUPC — account update (TRANID CAUP). The pseudo-conversational COMMAREA (ACUP-OLD-DETAILS /
 * ACUP-NEW-DETAILS) is replaced by the client echoing the fetched snapshot ({@code original}) with
 * each request; the server re-runs every edit on save and re-checks the snapshot against the
 * database (9700-CHECK-CHANGE-IN-REC), so no server-side session is needed.
 */
@Service
public class AccountUpdateService {
    public static final String MSG_VALIDATED = "Changes validated.Press F5 to save";
    public static final String MSG_COMMITTED = "Changes committed to database";
    public static final String INFO_SHOW = "Update account details presented above.";

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final CardXrefRepository xrefs;
    private final AccountUpdateValidator validator;
    private final BusinessClock clock;

    public AccountUpdateService(AccountRepository accounts, CustomerRepository customers, CardXrefRepository xrefs,
                                AccountUpdateValidator validator, BusinessClock clock) {
        this.accounts = accounts;
        this.customers = customers;
        this.xrefs = xrefs;
        this.validator = validator;
        this.clock = clock;
    }

    public record AccountUpdateData(String accountId, String customerId, String cardNumber, String info,
                                    AccountFields fields) {
    }

    public record UpdateRequest(AccountFields original, AccountFields changes) {
    }

    /** state: N = changes ok not confirmed, C = committed (ACUP-CHANGE-ACTION values). */
    public record Outcome(String state, String message, AccountFields fields) {
    }

    /** COACTUPC 1210-EDIT-ACCOUNT (note: message differs from COACTVWC). */
    static long editAccount(String input) {
        if (input == null || input.isBlank()) {
            throw new LegacyRuleException("No input received", "accountId", "COACTUPC 1210-EDIT-ACCOUNT");
        }
        if (!input.matches("\\d{11}") || Long.parseLong(input) == 0) {
            throw new LegacyRuleException("Account Number if supplied must be a 11 digit Non-Zero Number",
                    "accountId", "COACTUPC 1210-EDIT-ACCOUNT");
        }
        return Long.parseLong(input);
    }

    @Transactional(readOnly = true)
    public AccountUpdateData fetch(String accountIdInput) {
        long id = editAccount(accountIdInput);
        Loaded l = load(id);
        return new AccountUpdateData(LegacyFormat.zeroPad(id, 11), LegacyFormat.zeroPad(l.customer.getId(), 9),
                l.cardNum, INFO_SHOW, AccountFields.of(l.account, l.customer));
    }

    /** ENTER on the details screen: 1205-COMPARE-OLD-NEW then 1200-EDIT-MAP-INPUTS. */
    public Outcome validate(String accountIdInput, UpdateRequest req) {
        editAccount(accountIdInput);
        checkChangesAndEdit(req);
        return new Outcome("N", MSG_VALIDATED, redisplay(req.changes()));
    }

    /** PF5 in state N: 9600-WRITE-PROCESSING. */
    @Transactional
    @CacheEvict(cacheNames = "accountView", key = "#accountIdInput")
    public Outcome save(String accountIdInput, UpdateRequest req) {
        long id = editAccount(accountIdInput);
        checkChangesAndEdit(req);
        Account a = accounts.findForUpdate(id).orElseThrow(() -> LegacyRuleException.conflict(
                "Could not lock account record for update", "COACTUPC 9600-WRITE-PROCESSING"));
        CardXref x = xrefs.findFirstByAccountIdOrderByCardNumberAsc(id).orElseThrow(() -> LegacyRuleException.conflict(
                "Could not lock customer record for update", "COACTUPC 9600-WRITE-PROCESSING"));
        Customer c = customers.findForUpdate(x.getCustomerId()).orElseThrow(() -> LegacyRuleException.conflict(
                "Could not lock customer record for update", "COACTUPC 9600-WRITE-PROCESSING"));
        // ONL-ACU-02  9700-CHECK-CHANGE-IN-REC
        if (!same(AccountFields.of(a, c), req.original())) {
            throw LegacyRuleException.conflict("Record changed by some one else. Please review",
                    "COACTUPC 9700-CHECK-CHANGE-IN-REC");
        }
        apply(req.changes(), a, c);
        return new Outcome("C", MSG_COMMITTED, AccountFields.of(a, c));
    }

    private void checkChangesAndEdit(UpdateRequest req) {
        if (req == null || req.changes() == null || req.original() == null) {
            throw new LegacyRuleException("No input received", null, "COACTUPC 1200-EDIT-MAP-INPUTS");
        }
        if (same(req.changes(), req.original())) {
            throw new LegacyRuleException("No change detected with respect to values fetched.", null,
                    "COACTUPC 1205-COMPARE-OLD-NEW");
        }
        // ONL-ACU-01
        validator.validate(req.changes(), clock.now().toLocalDate());
    }

    /** 1205-COMPARE-OLD-NEW: text fields compared UPPER-CASE(TRIM()), money compared numerically. */
    static boolean same(AccountFields n, AccountFields o) {
        return eq(n.activeStatus(), o.activeStatus())
                && money(n.currentBalance(), o.currentBalance()) && money(n.creditLimit(), o.creditLimit())
                && money(n.cashCreditLimit(), o.cashCreditLimit())
                && eq(n.openYear(), o.openYear()) && eq(n.openMonth(), o.openMonth()) && eq(n.openDay(), o.openDay())
                && eq(n.expiryYear(), o.expiryYear()) && eq(n.expiryMonth(), o.expiryMonth())
                && eq(n.expiryDay(), o.expiryDay())
                && eq(n.reissueYear(), o.reissueYear()) && eq(n.reissueMonth(), o.reissueMonth())
                && eq(n.reissueDay(), o.reissueDay())
                && money(n.currentCycleCredit(), o.currentCycleCredit())
                && money(n.currentCycleDebit(), o.currentCycleDebit())
                && eq(n.groupId(), o.groupId())
                && eq(n.firstName(), o.firstName()) && eq(n.middleName(), o.middleName())
                && eq(n.lastName(), o.lastName()) && eq(n.addressLine1(), o.addressLine1())
                && eq(n.addressLine2(), o.addressLine2()) && eq(n.city(), o.city()) && eq(n.state(), o.state())
                && eq(n.country(), o.country()) && eq(n.zip(), o.zip())
                && eq(n.phone1a(), o.phone1a()) && eq(n.phone1b(), o.phone1b()) && eq(n.phone1c(), o.phone1c())
                && eq(n.phone2a(), o.phone2a()) && eq(n.phone2b(), o.phone2b()) && eq(n.phone2c(), o.phone2c())
                && eq(n.ssn1(), o.ssn1()) && eq(n.ssn2(), o.ssn2()) && eq(n.ssn3(), o.ssn3())
                && eq(n.governmentId(), o.governmentId())
                && eq(n.dobYear(), o.dobYear()) && eq(n.dobMonth(), o.dobMonth()) && eq(n.dobDay(), o.dobDay())
                && eq(n.eftAccountId(), o.eftAccountId()) && eq(n.primaryCardHolder(), o.primaryCardHolder())
                && eq(n.ficoScore(), o.ficoScore());
    }

    private static boolean eq(String a, String b) {
        return norm(a).equals(norm(b));
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean money(String a, String b) {
        BigDecimal x = Numval.parse(a).orElse(null);
        BigDecimal y = Numval.parse(b).orElse(null);
        return x != null && y != null ? x.compareTo(y) == 0 : Objects.equals(norm(a), norm(b));
    }

    /** 3203-SHOW-UPDATED-VALUES: money re-edited with +ZZZ,ZZZ,ZZZ.99 once valid. */
    private static AccountFields redisplay(AccountFields f) {
        return new AccountFields(f.activeStatus(), f.openYear(), f.openMonth(), f.openDay(), cur(f.creditLimit()),
                f.expiryYear(), f.expiryMonth(), f.expiryDay(), cur(f.cashCreditLimit()), f.reissueYear(),
                f.reissueMonth(), f.reissueDay(), cur(f.currentBalance()), cur(f.currentCycleCredit()),
                cur(f.currentCycleDebit()), f.groupId(), f.ssn1(), f.ssn2(), f.ssn3(), f.dobYear(), f.dobMonth(),
                f.dobDay(), f.ficoScore(), f.firstName(), f.middleName(), f.lastName(), f.addressLine1(),
                f.addressLine2(), f.city(), f.state(), f.zip(), f.country(), f.phone1a(), f.phone1b(), f.phone1c(),
                f.phone2a(), f.phone2b(), f.phone2c(), f.governmentId(), f.eftAccountId(), f.primaryCardHolder());
    }

    private static String cur(String v) {
        return Numval.parse(v).map(LegacyFormat::currency).orElse(v);
    }

    /** 9600-WRITE-PROCESSING: build ACCT-UPDATE-RECORD and CUST-UPDATE-RECORD. */
    private static void apply(AccountFields f, Account a, Customer c) {
        a.setActiveStatus(f.activeStatus().trim());
        a.setCreditLimit(num(f.creditLimit()));
        a.setCashCreditLimit(num(f.cashCreditLimit()));
        a.setCurrentBalance(num(f.currentBalance()));
        a.setCurrentCycleCredit(num(f.currentCycleCredit()));
        a.setCurrentCycleDebit(num(f.currentCycleDebit()));
        a.setOpenDate(LegacyDates.date(date(f.openYear(), f.openMonth(), f.openDay())));
        a.setExpirationDate(LegacyDates.date(date(f.expiryYear(), f.expiryMonth(), f.expiryDay())));
        a.setReissueDate(LegacyDates.date(date(f.reissueYear(), f.reissueMonth(), f.reissueDay())));
        a.setGroupId(t(f.groupId()));
        c.setFirstName(t(f.firstName()));
        c.setMiddleName(t(f.middleName()));
        c.setLastName(t(f.lastName()));
        c.setAddressLine1(t(f.addressLine1()));
        c.setAddressLine2(t(f.addressLine2()));
        c.setAddressLine3(t(f.city()));
        c.setStateCode(t(f.state()));
        c.setCountryCode(t(f.country()));
        c.setZip(t(f.zip()));
        c.setPhoneNumber1(phone(f.phone1a(), f.phone1b(), f.phone1c()));
        c.setPhoneNumber2(phone(f.phone2a(), f.phone2b(), f.phone2c()));
        c.setSsn(String.format("%09d", Long.parseLong(t(f.ssn1()) + t(f.ssn2()) + t(f.ssn3()))));
        c.setGovernmentIssuedId(t(f.governmentId()));
        c.setDateOfBirth(LegacyDates.date(date(f.dobYear(), f.dobMonth(), f.dobDay())));
        c.setEftAccountId(t(f.eftAccountId()));
        c.setPrimaryCardHolder(t(f.primaryCardHolder()));
        c.setFicoCreditScore(Integer.parseInt(t(f.ficoScore()).substring(0, 3)));
    }

    private static String t(String s) {
        return s == null ? "" : s.trim();
    }

    private static BigDecimal num(String s) {
        return Numval.parse(s).orElseThrow().setScale(2, java.math.RoundingMode.DOWN);
    }

    private static String date(String y, String m, String d) {
        return String.format("%04d-%02d-%02d", Integer.parseInt(t(y)), Integer.parseInt(t(m)), Integer.parseInt(t(d)));
    }

    /** Phones rebuilt as '(AAA)BBB-CCCC'; a phone accepted as blank is stored as spaces. */
    private static String phone(String a, String b, String c) {
        if (AccountUpdateValidator.blank(a) && AccountUpdateValidator.blank(b)) {
            return "";
        }
        return "(" + t(a) + ")" + t(b) + "-" + t(c);
    }

    private record Loaded(Account account, Customer customer, String cardNum) {
    }

    /** 9000-READ-ACCT: xref (CXACAIX) -> account -> customer, same messages as COACTVWC. */
    private Loaded load(long id) {
        String acct = LegacyFormat.zeroPad(id, 11);
        CardXref x = xrefs.findFirstByAccountIdOrderByCardNumberAsc(id).orElseThrow(() -> LegacyRuleException.notFound(
                "Account:" + acct + " not found in Cross ref file.  " + AccountViewService.RESP_NOTFND, "accountId",
                "COACTUPC 9200-GETCARDXREF-BYACCT"));
        Account a = accounts.findById(id).orElseThrow(() -> LegacyRuleException.notFound(
                "Account:" + acct + " not found in Acct Master file." + AccountViewService.RESP_NOTFND, "accountId",
                "COACTUPC 9300-GETACCTDATA-BYACCT"));
        Customer c = customers.findById(x.getCustomerId()).orElseThrow(() -> LegacyRuleException.notFound(
                "CustId:" + LegacyFormat.zeroPad(x.getCustomerId(), 9)
                        + " not found in customer master.Resp: 000000013  REAS:0000",
                "accountId", "COACTUPC 9400-GETCUSTDATA-BYCUST"));
        return new Loaded(a, c, x.getCardNumber());
    }
}
