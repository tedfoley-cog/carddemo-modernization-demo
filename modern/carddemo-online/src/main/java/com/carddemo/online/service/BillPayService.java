package com.carddemo.online.service;

import com.carddemo.online.legacy.LegacyDates;
import com.carddemo.online.config.BusinessClock;
import com.carddemo.domain.model.Account;
import com.carddemo.domain.model.CardXref;
import com.carddemo.domain.model.CardTransaction;
import com.carddemo.online.legacy.LegacyFormat;
import com.carddemo.online.repo.AccountRepository;
import com.carddemo.online.repo.CardXrefRepository;
import com.carddemo.online.repo.TransactionRepository;
import java.math.BigDecimal;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COBIL00C — bill payment (TRANID CB00). Pays the FULL current balance; there is no amount field. */
@Service
public class BillPayService {
    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final TransactionRepository transactions;
    private final TransactionIdGenerator ids;
    private final BusinessClock clock;
    private final CacheManager caches;

    public BillPayService(AccountRepository accounts, CardXrefRepository xrefs, TransactionRepository transactions,
                          TransactionIdGenerator ids, BusinessClock clock, CacheManager caches) {
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
        this.caches = caches;
    }

    /** message is the legacy ERRMSG text; transactionId set only after a successful payment. */
    public record BillPayResult(String accountId, String currentBalance, String message, String transactionId) {
    }

    /**
     * COBIL00C PROCESS-ENTER-KEY. confirm: blank = show balance and prompt, Y/y = pay,
     * N/n = clear, anything else = invalid.
     */
    @Transactional
    public BillPayResult process(String accountIdInput, String confirm) {
        String p = "COBIL00C PROCESS-ENTER-KEY";
        // ONL-BIL-01
        if (accountIdInput == null || accountIdInput.isBlank()) {
            throw new LegacyRuleException("Acct ID can NOT be empty...", "accountId", p);
        }
        String c = confirm == null ? "" : confirm.trim();
        if (c.equals("N") || c.equals("n")) {
            return new BillPayResult("", "", "", null);
        }
        if (!c.isEmpty() && !c.equals("Y") && !c.equals("y")) {
            throw new LegacyRuleException("Invalid value. Valid values are (Y/N)...", "confirm", p);
        }
        long id = parseAcct(accountIdInput);
        Account a = (c.isEmpty() ? accounts.findById(id) : accounts.findForUpdate(id))
                .orElseThrow(() -> LegacyRuleException.notFound("Account ID NOT found...", "accountId",
                        "COBIL00C READ-ACCTDAT-FILE"));
        String balance = LegacyFormat.signedAmount(a.getCurrentBalance(), 10);
        // ONL-BIL-02
        if (a.getCurrentBalance().signum() <= 0) {
            throw new LegacyRuleException("You have nothing to pay...", "accountId", p);
        }
        if (c.isEmpty()) {
            return new BillPayResult(LegacyFormat.zeroPad(id, 11), balance, "Confirm to make a bill payment...", null);
        }
        // ONL-BIL-03  READ-CXACAIX-FILE, WRITE-TRANSACT-FILE, UPDATE-ACCTDAT-FILE
        CardXref x = xrefs.findFirstByAccountIdOrderByCardNumberAsc(id).orElseThrow(() -> LegacyRuleException.notFound(
                "Account ID NOT found...", "accountId", "COBIL00C READ-CXACAIX-FILE"));
        CardTransaction t = new CardTransaction();
        t.setTransactionId(ids.next());
        t.setTypeCode("02");
        t.setCategoryCode(2);
        t.setSource("POS TERM");
        t.setDescription("BILL PAYMENT - ONLINE");
        t.setAmount(a.getCurrentBalance());
        t.setCardNumber(x.getCardNumber());
        t.setMerchantId(999999999L);
        t.setMerchantName("BILL PAYMENT");
        t.setMerchantCity("N/A");
        t.setMerchantZip("N/A");
        String ts = clock.legacyTimestamp();
        LegacyDates.stamp(t, ts, ts);
        transactions.save(t);
        a.setCurrentBalance(a.getCurrentBalance().subtract(t.getAmount()));
        var cache = caches.getCache("accountView");
        if (cache != null) {
            cache.evict(LegacyFormat.zeroPad(id, 11));
        }
        return new BillPayResult(LegacyFormat.zeroPad(id, 11), LegacyFormat.signedAmount(a.getCurrentBalance(), 10),
                "Payment successful.  Your Transaction ID is " + t.getTransactionId() + ".", t.getTransactionId());
    }

    /** ACTIDINI is moved to an X(11) key; a non-numeric or short id simply is not found. */
    private static long parseAcct(String s) {
        String t = s.trim();
        if (!t.matches("\\d{1,11}")) {
            throw LegacyRuleException.notFound("Account ID NOT found...", "accountId", "COBIL00C READ-ACCTDAT-FILE");
        }
        return Long.parseLong(t);
    }

    static BigDecimal zero() {
        return BigDecimal.ZERO;
    }
}
