package com.carddemo.online.service;

import com.carddemo.online.domain.Card;
import com.carddemo.online.repo.CardRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COCRDLIC card list (CCLI), COCRDSLC card view (CCDL), COCRDUPC card update (CCUP). */
@Service
public class CardService {
    public static final int PAGE_SIZE = 7;
    static final String ACCT_FILTER = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    static final String CARD_FILTER = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";

    private final CardRepository cards;

    public CardService(CardRepository cards) {
        this.cards = cards;
    }

    public record CardRow(String accountId, String cardNumber, String activeStatus) {
    }

    public record CardPage(int page, List<CardRow> rows, boolean hasNext, String info) {
    }

    public record CardDetail(String accountId, String cardNumber, String embossedName, String activeStatus,
                             String expiryMonth, String expiryYear, String info) {
    }

    public record CardChanges(String embossedName, String activeStatus, String expiryMonth, String expiryYear) {
    }

    public record CardUpdateRequest(CardChanges original, CardChanges changes) {
    }

    public record CardOutcome(String state, String message, CardDetail card) {
    }

    /**
     * COCRDLIC 9000-READ-FORWARD / 9100-READ-BACKWARDS with 2210/2220 filter edits. {@code after} is the
     * last card on the current page (PF8), {@code before} the first card on it (PF7).
     */
    @Transactional(readOnly = true)
    public CardPage list(String acctFilter, String cardFilter, String after, String before, int page) {
        Long acct = null;
        String card = null;
        if (!blank(acctFilter)) {
            // QUIRK-CRD-01: only IS NUMERIC is tested; a short numeric filter is accepted
            if (!acctFilter.trim().chars().allMatch(Character::isDigit)) {
                throw new LegacyRuleException(ACCT_FILTER, "accountId", "COCRDLIC 2210-EDIT-ACCOUNT");
            }
            acct = Long.parseLong(acctFilter.trim());
        }
        if (!blank(cardFilter)) {
            if (!cardFilter.trim().chars().allMatch(Character::isDigit)) {
                throw new LegacyRuleException(CARD_FILTER, "cardNumber", "COCRDLIC 2220-EDIT-CARD");
            }
            card = String.format("%016d", Long.parseLong(cardFilter.trim()));
        }
        if (before != null) {
            if (page <= 1) {
                throw new LegacyRuleException("NO PREVIOUS PAGES TO DISPLAY", null, "COCRDLIC 2000-PROCESS-INPUTS");
            }
            String b = before;
            List<Card> prev = new ArrayList<>(cards.browseBackward(before, acct, card,
                    PageRequest.of(0, PAGE_SIZE + 1)).stream().filter(c -> !c.getCardNum().equals(b)).toList());
            List<Card> rows = new ArrayList<>(prev.subList(0, Math.min(PAGE_SIZE, prev.size())));
            java.util.Collections.reverse(rows);
            return new CardPage(page - 1, toRows(rows), true, "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");
        }
        String from = after == null ? "" : after;
        List<Card> found = cards.browseForward(from, acct, card, PageRequest.of(0, PAGE_SIZE + 2)).stream()
                .filter(c -> after == null || !c.getCardNum().equals(after)).toList();
        if (found.isEmpty()) {
            throw new LegacyRuleException(after == null ? "NO RECORDS FOUND FOR THIS SEARCH CONDITION."
                    : "NO MORE PAGES TO DISPLAY", null, "COCRDLIC 9000-READ-FORWARD");
        }
        List<Card> rows = found.subList(0, Math.min(PAGE_SIZE, found.size()));
        return new CardPage(after == null ? 1 : page + 1, toRows(rows), found.size() > PAGE_SIZE,
                "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");
    }

    /** COCRDLIC 2250-EDIT-ARRAY: one selection, S or U. */
    public static void editSelections(List<String> actions) {
        long selected = actions.stream().filter(a -> !blank(a)).count();
        if (selected > 1) {
            throw new LegacyRuleException("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE", "selection",
                    "COCRDLIC 2250-EDIT-ARRAY");
        }
        for (String a : actions) {
            if (!blank(a) && !a.equals("S") && !a.equals("U")) {
                throw new LegacyRuleException("INVALID ACTION CODE", "selection", "COCRDLIC 2250-EDIT-ARRAY");
            }
        }
    }

    /** COCRDSLC 2200-EDIT-MAP-INPUTS + 9100-GETCARD-BYACCTCARD (read by card number only). */
    @Transactional(readOnly = true)
    public CardDetail view(String acctIn, String cardIn) {
        Card c = read(acctIn, cardIn, "COCRDSLC");
        return detail(c, "   Displaying requested details");
    }

    @Transactional(readOnly = true)
    public CardDetail fetchForUpdate(String acctIn, String cardIn) {
        Card c = read(acctIn, cardIn, "COCRDUPC");
        return detail(c, "Details of selected card shown above");
    }

    /** COCRDUPC 1200-EDIT-MAP-INPUTS. */
    public CardOutcome validate(String acctIn, String cardIn, CardUpdateRequest req) {
        keys(acctIn, cardIn, "COCRDUPC");
        editChanges(req);
        return new CardOutcome("N", AccountUpdateService.MSG_VALIDATED, null);
    }

    /** COCRDUPC 9200-WRITE-PROCESSING (READ UPDATE, 9300-CHECK-CHANGE-IN-REC, REWRITE). */
    @Transactional
    public CardOutcome save(String acctIn, String cardIn, CardUpdateRequest req) {
        String cardNum = keys(acctIn, cardIn, "COCRDUPC");
        editChanges(req);
        Card c = cards.findForUpdate(cardNum).orElseThrow(() -> LegacyRuleException.conflict(
                "Could not lock record for update", "COCRDUPC 9200-WRITE-PROCESSING"));
        if (!same(current(c), req.original())) {
            throw LegacyRuleException.conflict("Record changed by some one else. Please review",
                    "COCRDUPC 9300-CHECK-CHANGE-IN-REC");
        }
        CardChanges n = req.changes();
        c.setEmbossedName(n.embossedName().trim());
        c.setActiveStatus(n.activeStatus().trim());
        String day = c.getExpirationDate() != null && c.getExpirationDate().length() >= 10
                ? c.getExpirationDate().substring(8, 10) : "01";
        c.setExpirationDate(String.format("%04d-%02d-%s", Integer.parseInt(n.expiryYear().trim()),
                Integer.parseInt(n.expiryMonth().trim()), day));
        return new CardOutcome("C", AccountUpdateService.MSG_COMMITTED, detail(c, "Changes committed to database"));
    }

    void editChanges(CardUpdateRequest req) {
        if (req == null || req.changes() == null || req.original() == null) {
            throw new LegacyRuleException("No input received", null, "COCRDUPC 1200-EDIT-MAP-INPUTS");
        }
        if (same(req.changes(), req.original())) {
            throw new LegacyRuleException("No change detected with respect to values fetched.", null,
                    "COCRDUPC 1200-EDIT-MAP-INPUTS");
        }
        CardChanges n = req.changes();
        if (blankOrZero(n.embossedName())) {
            throw new LegacyRuleException("Card name not provided", "embossedName", "COCRDUPC 1230-EDIT-NAME");
        }
        if (!n.embossedName().matches("[A-Za-z ]*")) {
            throw new LegacyRuleException("Card name can only contain alphabets and spaces", "embossedName",
                    "COCRDUPC 1230-EDIT-NAME");
        }
        if (blankOrZero(n.activeStatus()) || !(n.activeStatus().equals("Y") || n.activeStatus().equals("N"))) {
            throw new LegacyRuleException("Card Active Status must be Y or N", "activeStatus",
                    "COCRDUPC 1240-EDIT-CARDSTATUS");
        }
        int m = num(n.expiryMonth(), 2);
        if (m < 1 || m > 12) {
            throw new LegacyRuleException("Card expiry month must be between 1 and 12", "expiryMonth",
                    "COCRDUPC 1250-EDIT-EXPIRY-MON");
        }
        int y = num(n.expiryYear(), 4);
        if (y < 1950 || y > 2099) {
            throw new LegacyRuleException("Invalid card expiry year", "expiryYear", "COCRDUPC 1260-EDIT-EXPIRY-YEAR");
        }
    }

    private Card read(String acctIn, String cardIn, String program) {
        String cardNum = keys(acctIn, cardIn, program);
        return cards.findById(cardNum).orElseThrow(() -> LegacyRuleException.notFound(
                "Did not find cards for this search condition", "cardNumber", program + " 9100-GETCARD-BYACCTCARD"));
    }

    /** 2210-EDIT-ACCOUNT / 2220-EDIT-CARD; blank or zero means "not entered". Returns the card key. */
    static String keys(String acctIn, String cardIn, String program) {
        boolean acctBlank = blankOrZero(acctIn);
        boolean cardBlank = blankOrZero(cardIn);
        if (acctBlank && cardBlank) {
            throw new LegacyRuleException("No input received", "accountId", program + " 2200-EDIT-MAP-INPUTS");
        }
        if (acctBlank) {
            throw new LegacyRuleException("Account number not provided", "accountId", program + " 2210-EDIT-ACCOUNT");
        }
        if (!acctIn.matches("\\d{11}")) {
            throw new LegacyRuleException(ACCT_FILTER, "accountId", program + " 2210-EDIT-ACCOUNT");
        }
        if (cardBlank) {
            throw new LegacyRuleException("Card number not provided", "cardNumber", program + " 2220-EDIT-CARD");
        }
        if (!cardIn.matches("\\d{16}")) {
            throw new LegacyRuleException(CARD_FILTER, "cardNumber", program + " 2220-EDIT-CARD");
        }
        return cardIn;
    }

    private static CardChanges current(Card c) {
        String exp = c.getExpirationDate() == null ? "" : c.getExpirationDate();
        return new CardChanges(c.getEmbossedName(), c.getActiveStatus(), AccountFields.part(exp, 5, 7),
                AccountFields.part(exp, 0, 4));
    }

    private static CardDetail detail(Card c, String info) {
        CardChanges cur = current(c);
        return new CardDetail(String.format("%011d", c.getAcctId()), c.getCardNum(), c.getEmbossedName(),
                c.getActiveStatus(), cur.expiryMonth(), cur.expiryYear(), info);
    }

    static boolean same(CardChanges a, CardChanges b) {
        return n(a.embossedName()).equals(n(b.embossedName())) && n(a.activeStatus()).equals(n(b.activeStatus()))
                && num(a.expiryMonth(), 2) == num(b.expiryMonth(), 2) && num(a.expiryYear(), 4) == num(b.expiryYear(), 4);
    }

    private static String n(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static int num(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() || t.length() > max || !t.chars().allMatch(Character::isDigit) ? -1 : Integer.parseInt(t);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank() || "*".equals(s.trim());
    }

    private static boolean blankOrZero(String s) {
        return blank(s) || s.trim().chars().allMatch(ch -> ch == '0');
    }

    private static List<CardRow> toRows(List<Card> list) {
        return list.stream().map(c -> new CardRow(String.format("%011d", c.getAcctId()), c.getCardNum(),
                c.getActiveStatus())).toList();
    }
}
