package com.carddemo.online.service;

import com.carddemo.online.domain.CardXref;
import com.carddemo.online.domain.Transaction;
import com.carddemo.online.legacy.Numval;
import com.carddemo.online.repo.CardXrefRepository;
import com.carddemo.online.repo.TransactionRepository;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COTRN02C — add transaction (TRANID CT02). Every edit SENDs and RETURNs, so the first failure wins. */
@Service
public class TransactionAddService {
    private static final String P = "COTRN02C ";
    private final CardXrefRepository xrefs;
    private final TransactionRepository transactions;
    private final TransactionIdGenerator ids;

    public TransactionAddService(CardXrefRepository xrefs, TransactionRepository transactions,
                                 TransactionIdGenerator ids) {
        this.xrefs = xrefs;
        this.transactions = transactions;
        this.ids = ids;
    }

    public record AddRequest(String accountId, String cardNumber, String typeCd, String categoryCd, String source,
                             String description, String amount, String origDate, String procDate, String merchantId,
                             String merchantName, String merchantCity, String merchantZip, String confirm) {
    }

    /** message = legacy ERRMSG; when confirm is blank the resolved account/card are echoed back. */
    public record AddResult(String accountId, String cardNumber, String amount, String message,
                            String transactionId) {
    }

    @Transactional
    public AddResult add(AddRequest r) {
        CardXref x = validateKeyFields(r);
        validateDataFields(r);
        String amount = String.format("%+012.2f", Numval.parse(r.amount()).orElseThrow());
        String c = r.confirm() == null ? "" : r.confirm().trim();
        switch (c) {
            case "Y", "y" -> {
                // ADD-TRANSACTION
                Transaction t = new Transaction();
                t.setTranId(ids.next());
                t.setTypeCd(r.typeCd().trim());
                t.setCatCd(Integer.parseInt(r.categoryCd().trim()));
                t.setSource(r.source());
                t.setDesc(r.description());
                t.setAmt(Numval.parse(r.amount()).orElseThrow());
                t.setCardNum(x.getCardNum());
                t.setMerchantId(Long.parseLong(r.merchantId().trim()));
                t.setMerchantName(r.merchantName());
                t.setMerchantCity(r.merchantCity());
                t.setMerchantZip(r.merchantZip());
                t.setOrigTs(r.origDate());
                t.setProcTs(r.procDate());
                transactions.save(t);
                return new AddResult(acct(x), x.getCardNum(), amount,
                        "Transaction added successfully.  Your Tran ID is " + t.getTranId() + ".", t.getTranId());
            }
            case "N", "n", "" -> throw new LegacyRuleException("Confirm to add this transaction...", "confirm",
                    P + "PROCESS-ENTER-KEY");
            default -> throw new LegacyRuleException("Invalid value. Valid values are (Y/N)...", "confirm",
                    P + "PROCESS-ENTER-KEY");
        }
    }

    /** VALIDATE-INPUT-KEY-FIELDS: account wins over card when both are entered. ONL-TRA-01 */
    CardXref validateKeyFields(AddRequest r) {
        String para = P + "VALIDATE-INPUT-KEY-FIELDS";
        if (!blank(r.accountId())) {
            if (!r.accountId().trim().chars().allMatch(Character::isDigit)) {
                throw new LegacyRuleException("Account ID must be Numeric...", "accountId", para);
            }
            long id = parseKey(r.accountId(), 11, "Account ID NOT found...", "accountId", P + "READ-CXACAIX-FILE");
            return xrefs.findFirstByAcctIdOrderByCardNumAsc(id).orElseThrow(() -> LegacyRuleException.notFound(
                    "Account ID NOT found...", "accountId", P + "READ-CXACAIX-FILE"));
        }
        if (!blank(r.cardNumber())) {
            if (!r.cardNumber().trim().chars().allMatch(Character::isDigit)) {
                throw new LegacyRuleException("Card Number must be Numeric...", "cardNumber", para);
            }
            String card = String.format("%016d",
                    parseKey(r.cardNumber(), 16, "Card Number NOT found...", "cardNumber", P + "READ-CCXREF-FILE"));
            return xrefs.findById(card).orElseThrow(() -> LegacyRuleException.notFound(
                    "Card Number NOT found...", "cardNumber", P + "READ-CCXREF-FILE"));
        }
        throw new LegacyRuleException("Account or Card Number must be entered...", "accountId", para);
    }

    /** ACTIDINI X(11) / CARDNINI X(16): a key longer than the map field cannot exist in the file. */
    private static long parseKey(String s, int width, String notFound, String field, String para) {
        String t = s.trim();
        if (t.length() > width) {
            throw LegacyRuleException.notFound(notFound, field, para);
        }
        return Long.parseLong(t);
    }

    /** VALIDATE-INPUT-DATA-FIELDS (TCATCD X(4), MIDI X(9)). */
    void validateDataFields(AddRequest r) {
        String para = P + "VALIDATE-INPUT-DATA-FIELDS";
        required(r.typeCd(), "Type CD", "typeCd", para);
        required(r.categoryCd(), "Category CD", "categoryCd", para);
        required(r.source(), "Source", "source", para);
        required(r.description(), "Description", "description", para);
        required(r.amount(), "Amount", "amount", para);
        required(r.origDate(), "Orig Date", "origDate", para);
        required(r.procDate(), "Proc Date", "procDate", para);
        required(r.merchantId(), "Merchant ID", "merchantId", para);
        required(r.merchantName(), "Merchant Name", "merchantName", para);
        required(r.merchantCity(), "Merchant City", "merchantCity", para);
        required(r.merchantZip(), "Merchant Zip", "merchantZip", para);
        check(digits(r.typeCd()), () -> new LegacyRuleException("Type CD must be Numeric...", "typeCd", para));
        check(digits(r.categoryCd()) && r.categoryCd().trim().length() <= 4,
                () -> new LegacyRuleException("Category CD must be Numeric...", "categoryCd", para));
        check(r.amount().matches("[-+]\\d{8}\\.\\d{2}.*"),
                () -> new LegacyRuleException("Amount should be in format -99999999.99", "amount", para));
        check(r.origDate().matches("\\d{4}-\\d{2}-\\d{2}.*"),
                () -> new LegacyRuleException("Orig Date should be in format YYYY-MM-DD", "origDate", para));
        check(r.procDate().matches("\\d{4}-\\d{2}-\\d{2}.*"),
                () -> new LegacyRuleException("Proc Date should be in format YYYY-MM-DD", "procDate", para));
        check(validDate(r.origDate()),
                () -> new LegacyRuleException("Orig Date - Not a valid date...", "origDate", P + "CSUTLDTC"));
        check(validDate(r.procDate()),
                () -> new LegacyRuleException("Proc Date - Not a valid date...", "procDate", P + "CSUTLDTC"));
        check(digits(r.merchantId()) && r.merchantId().trim().length() <= 9,
                () -> new LegacyRuleException("Merchant ID must be Numeric...", "merchantId", para));
    }

    /** CSUTLDTC (CEEDAYS) — result 2513 (date outside the Lilian range) is ignored by the legacy code. */
    static boolean validDate(String s) {
        int year = Integer.parseInt(s.substring(0, 4));
        if (year < 1601) {
            return true; // QUIRK-TRN-02: CEEDAYS msg 2513 is not treated as an error
        }
        try {
            LocalDate.of(year, Integer.parseInt(s.substring(5, 7)), Integer.parseInt(s.substring(8, 10)));
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static void required(String v, String name, String field, String para) {
        if (blank(v)) {
            throw new LegacyRuleException(name + " can NOT be empty...", field, para);
        }
    }

    private static void check(boolean ok, Supplier<LegacyRuleException> e) {
        if (!ok) {
            throw e.get();
        }
    }

    private static boolean digits(String s) {
        return s != null && !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String acct(CardXref x) {
        return String.format("%011d", x.getAcctId());
    }

    static BigDecimal amount(String s) {
        return Numval.parse(s).orElseThrow();
    }
}
