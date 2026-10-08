package com.carddemo.batch.statement;

/**
 * Byte-faithful model of CBSTM03A {@code WS-TRNX-TABLE}: {@code WS-CARD-TBL OCCURS 51} of a
 * 16-byte card number followed by {@code WS-TRAN-TBL OCCURS 10} of a 16-byte id and 318-byte
 * rest. Storage is contiguous, so an 11th transaction for a card lands on the next card's
 * slot exactly as it does in the COBOL (documented in LEGACY-DEFECTS.md). Subscripts that fall
 * outside the whole table are undefined behaviour on the mainframe and are rejected here.
 * <p>Requirements: BAT-STM-01 (docs/BUSINESS_REQUIREMENTS.md).
 */
final class StatementTable {

    static final int LEGACY_CARDS = 51;
    static final int LEGACY_TRANSACTIONS = 10;
    private static final int CARD_LEN = 16;
    private static final int TRAN_LEN = 16 + 318;

    private final int cardCapacity;
    private final int tranCapacity;
    private final int entryLen;
    private final char[] storage;
    private final int[] counts;

    StatementTable(int cardCapacity, int tranCapacity) {
        this.cardCapacity = cardCapacity;
        this.tranCapacity = tranCapacity;
        this.entryLen = CARD_LEN + tranCapacity * TRAN_LEN;
        this.storage = " ".repeat(cardCapacity * entryLen).toCharArray();
        this.counts = new int[cardCapacity];
    }

    void putCard(int card, String cardNumber) {
        put(cardOffset(card), cardNumber, CARD_LEN);
    }

    String card(int card) {
        return get(cardOffset(card), CARD_LEN);
    }

    void putTransaction(int card, int tran, String id, String rest) {
        int offset = tranOffset(card, tran);
        put(offset, id, 16);
        put(offset + 16, rest, 318);
    }

    String transactionId(int card, int tran) {
        return get(tranOffset(card, tran), 16);
    }

    String transactionRest(int card, int tran) {
        return get(tranOffset(card, tran) + 16, 318);
    }

    void setCount(int card, int count) {
        checkCard(card);
        counts[card - 1] = count;
    }

    int count(int card) {
        checkCard(card);
        return counts[card - 1];
    }

    private int cardOffset(int card) {
        checkCard(card);
        return (card - 1) * entryLen;
    }

    private int tranOffset(int card, int tran) {
        return cardOffset(card) + CARD_LEN + (tran - 1) * TRAN_LEN;
    }

    private void checkCard(int card) {
        if (card < 1 || card > cardCapacity) {
            throw new StatementTableOverflowException("WS-CARD-TBL subscript " + card + " exceeds OCCURS " + cardCapacity);
        }
    }

    private void put(int offset, String value, int length) {
        check(offset, length);
        for (int i = 0; i < length; i++) {
            storage[offset + i] = i < value.length() ? value.charAt(i) : ' ';
        }
    }

    private String get(int offset, int length) {
        check(offset, length);
        return new String(storage, offset, length);
    }

    private void check(int offset, int length) {
        if (offset + length > storage.length) {
            throw new StatementTableOverflowException("reference beyond WS-TRNX-TABLE (cards=" + cardCapacity
                    + ", transactions=" + tranCapacity + ")");
        }
    }

    int tranCapacity() {
        return tranCapacity;
    }
}
