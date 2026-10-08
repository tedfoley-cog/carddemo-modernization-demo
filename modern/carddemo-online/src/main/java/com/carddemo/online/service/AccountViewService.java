package com.carddemo.online.service;

import com.carddemo.online.domain.Account;
import com.carddemo.online.domain.CardXref;
import com.carddemo.online.domain.Customer;
import com.carddemo.online.legacy.LegacyFormat;
import com.carddemo.online.repo.AccountRepository;
import com.carddemo.online.repo.CardXrefRepository;
import com.carddemo.online.repo.CustomerRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COACTVWC — account view (TRANID CAVW). */
@Service
public class AccountViewService {
    static final String RESP_NOTFND = "Resp:000000013  Reas:0000";

    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final CustomerRepository customers;

    public AccountViewService(AccountRepository accounts, CardXrefRepository xrefs, CustomerRepository customers) {
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.customers = customers;
    }

    /** Field names follow the CACTVWAO map (ACCTSID, ACRDLIM, ...) with legacy edit masks applied. */
    public record AccountView(String accountId, String activeStatus, String openDate, String creditLimit,
                              String expirationDate, String cashCreditLimit, String reissueDate,
                              String currentBalance, String currentCycleCredit, String groupId,
                              String currentCycleDebit, String customerId, String ssn, String dateOfBirth,
                              String ficoScore, String firstName, String middleName, String lastName,
                              String addressLine1, String addressLine2, String city, String state, String zip,
                              String country, String phone1, String phone2, String governmentId,
                              String eftAccountId, String primaryCardHolder, String cardNumber)
            implements java.io.Serializable {
    }

    /** COACTVWC 2210-EDIT-ACCOUNT. Returns the 11-digit account id. Shared by COACTUPC/COBIL00C. */
    public static long editAccount(String input, String program) {
        // ONL-ACV-01
        if (input == null || input.isBlank()) {
            throw new LegacyRuleException("No input received", "accountId", program + " 2210-EDIT-ACCOUNT");
        }
        if (!input.matches("\\d{11}") || Long.parseLong(input) == 0) {
            throw new LegacyRuleException("Account Filter must  be a non-zero 11 digit number", "accountId",
                    program + " 2210-EDIT-ACCOUNT");
        }
        return Long.parseLong(input);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "accountView", key = "#accountIdInput")
    public AccountView view(String accountIdInput) {
        long id = editAccount(accountIdInput, "COACTVWC");
        String acct = LegacyFormat.zeroPad(id, 11);
        // ONL-ACV-02  COACTVWC 9200-GETCARDXREF-BYACCT (READ CXACAIX)
        CardXref xref = xrefs.findFirstByAcctIdOrderByCardNumAsc(id).orElseThrow(() -> LegacyRuleException.notFound(
                "Account:" + acct + " not found in Cross ref file.  " + RESP_NOTFND, "accountId",
                "COACTVWC 9200-GETCARDXREF-BYACCT"));
        // COACTVWC 9300-GETACCTDATA-BYACCT
        Account a = accounts.findById(id).orElseThrow(() -> LegacyRuleException.notFound(
                "Account:" + acct + " not found in Acct Master file." + RESP_NOTFND, "accountId",
                "COACTVWC 9300-GETACCTDATA-BYACCT"));
        // COACTVWC 9400-GETCUSTDATA-BYCUST
        Customer c = customers.findById(xref.getCustId()).orElseThrow(() -> LegacyRuleException.notFound(
                "CustId:" + LegacyFormat.zeroPad(xref.getCustId(), 9)
                        + " not found in customer master.Resp: 000000013  REAS:0000",
                "accountId", "COACTVWC 9400-GETCUSTDATA-BYCUST"));
        return toView(a, c, xref.getCardNum());
    }

    /** COACTVWC 1200-SETUP-SCREEN-VARS. */
    static AccountView toView(Account a, Customer c, String cardNum) {
        return new AccountView(
                LegacyFormat.zeroPad(a.getAcctId(), 11), a.getActiveStatus(), a.getOpenDate(),
                LegacyFormat.currency(a.getCreditLimit()), a.getExpirationDate(),
                LegacyFormat.currency(a.getCashCreditLimit()), a.getReissueDate(),
                LegacyFormat.currency(a.getCurrBal()), LegacyFormat.currency(a.getCurrCycCredit()),
                a.getGroupId(), LegacyFormat.currency(a.getCurrCycDebit()),
                LegacyFormat.zeroPad(c.getCustId(), 9), LegacyFormat.ssn(c.getSsn()), c.getDob(),
                LegacyFormat.zeroPad(c.getFicoScore(), 3), c.getFirstName(), c.getMiddleName(), c.getLastName(),
                c.getAddrLine1(), c.getAddrLine2(), c.getAddrLine3(), c.getStateCd(), c.getZip(),
                c.getCountryCd(), c.getPhone1(), c.getPhone2(), c.getGovtIssuedId(), c.getEftAccountId(),
                c.getPriCardHolderInd(), cardNum);
    }
}
