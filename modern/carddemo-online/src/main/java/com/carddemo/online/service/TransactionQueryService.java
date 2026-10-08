package com.carddemo.online.service;

import com.carddemo.online.domain.Transaction;
import com.carddemo.online.legacy.LegacyFormat;
import com.carddemo.online.repo.TransactionRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COTRN00C transaction list (CT00) and COTRN01C transaction view (CT01). */
@Service
public class TransactionQueryService {
    public static final int PAGE_SIZE = 10;
    private final TransactionRepository transactions;

    public TransactionQueryService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    public record TranRow(String transactionId, String date, String description, String amount) {
    }

    public record TranPage(int page, List<TranRow> rows, boolean hasNext, String message) {
    }

    public record TranDetail(String transactionId, String cardNumber, String typeCd, String categoryCd,
                             String source, String description, String amount, String origDate, String procDate,
                             String merchantId, String merchantName, String merchantCity, String merchantZip) {
    }

    /**
     * COTRN00C PROCESS-ENTER-KEY / PROCESS-PF7-KEY / PROCESS-PF8-KEY. {@code fromId} is the TRNIDIN
     * search key; {@code after}/{@code before} are the last/first ids of the current page.
     */
    @Transactional(readOnly = true)
    public TranPage list(String fromId, String after, String before, int page) {
        if (before != null) {
            if (page <= 1) {
                throw new LegacyRuleException("You are already at the top of the page...", null,
                        "COTRN00C PROCESS-PF7-KEY");
            }
            List<Transaction> prev = new ArrayList<>(transactions.findByTranIdLessThanOrderByTranIdDesc(before,
                    PageRequest.of(0, PAGE_SIZE)));
            Collections.reverse(prev);
            return new TranPage(page - 1, rows(prev), true, "");
        }
        if (after != null) {
            List<Transaction> next = transactions.findByTranIdGreaterThanOrderByTranIdAsc(after,
                    PageRequest.of(0, PAGE_SIZE + 1));
            if (next.isEmpty()) {
                throw new LegacyRuleException("You are already at the bottom of the page...", null,
                        "COTRN00C PROCESS-PF8-KEY");
            }
            return page(page + 1, next);
        }
        String key = "";
        if (fromId != null && !fromId.isBlank()) {
            if (!fromId.trim().chars().allMatch(Character::isDigit)) {
                throw new LegacyRuleException("Tran ID must be Numeric ...", "transactionId",
                        "COTRN00C PROCESS-ENTER-KEY");
            }
            key = fromId.trim();
        }
        return page(1, transactions.findByTranIdGreaterThanEqualOrderByTranIdAsc(key, PageRequest.of(0, PAGE_SIZE + 1)));
    }

    /** COTRN00C PROCESS-ENTER-KEY selection column: only S/s is valid. */
    public static void editSelection(String sel) {
        if (sel != null && !sel.isBlank() && !sel.equals("S") && !sel.equals("s")) {
            throw new LegacyRuleException("Invalid selection. Valid value is S", "selection",
                    "COTRN00C PROCESS-ENTER-KEY");
        }
    }

    /** COTRN01C PROCESS-ENTER-KEY / READ-TRANSACT-FILE. */
    @Transactional(readOnly = true)
    public TranDetail view(String tranId) {
        if (tranId == null || tranId.isBlank()) {
            throw new LegacyRuleException("Tran ID can NOT be empty...", "transactionId", "COTRN01C PROCESS-ENTER-KEY");
        }
        // TRNIDIN is moved as-is (left-justified) into TRAN-ID; a short id is not zero-padded
        String key = tranId.trim();
        Transaction t = transactions.findById(key).orElseThrow(() -> LegacyRuleException.notFound(
                "Transaction ID NOT found...", "transactionId", "COTRN01C READ-TRANSACT-FILE"));
        return new TranDetail(t.getTranId(), t.getCardNum(), t.getTypeCd(), String.format("%04d", t.getCatCd()),
                t.getSource(), t.getDesc(), LegacyFormat.signedAmount(t.getAmt(), 8), date10(t.getOrigTs()), date10(t.getProcTs()),
                String.format("%09d", t.getMerchantId()), t.getMerchantName(), t.getMerchantCity(),
                t.getMerchantZip());
    }

    private static TranPage page(int pageNo, List<Transaction> found) {
        List<Transaction> rows = found.subList(0, Math.min(PAGE_SIZE, found.size()));
        return new TranPage(pageNo, rows(rows), found.size() > PAGE_SIZE, "");
    }

    private static List<TranRow> rows(List<Transaction> list) {
        return list.stream().map(t -> new TranRow(t.getTranId(), LegacyFormat.mmddyy(t.getOrigTs()),
                truncate(t.getDesc(), 26), LegacyFormat.signedAmount(t.getAmt(), 8))).toList();
    }

    /** TDESCnn on COTRN0A is 26 bytes; the MOVE truncates. */
    private static String truncate(String s, int len) {
        return s == null ? "" : s.length() <= len ? s : s.substring(0, len);
    }

    private static String date10(String ts) {
        return ts == null || ts.length() < 10 ? "" : ts.substring(0, 10);
    }
}
