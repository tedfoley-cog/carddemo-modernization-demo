package com.carddemo.batch.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** BAT-STM-01: WS-CARD-TBL OCCURS 51 / WS-TRAN-TBL OCCURS 10 (CBSTM03A.CBL:226-232). */
class StatementTableTest {

    @Test
    void batStm01_eleventhTransactionOverwritesNextCardSlot() {
        StatementTable table = new StatementTable(StatementTable.LEGACY_CARDS, StatementTable.LEGACY_TRANSACTIONS);
        table.putCard(1, "1111111111111111");
        table.putCard(2, "2222222222222222");
        table.putTransaction(1, 11, "OVERFLOWTRAN0001", "rest");
        assertThat(table.card(2)).isEqualTo("OVERFLOWTRAN0001");
        assertThat(table.transactionId(1, 11)).isEqualTo("OVERFLOWTRAN0001");
        assertThat(table.transactionRest(1, 11)).startsWith("rest");
    }

    @Test
    void batStm01_countsAreKeptPerCard() {
        StatementTable table = new StatementTable(2, 10);
        table.setCount(2, 7);
        assertThat(table.count(2)).isEqualTo(7);
        assertThat(table.count(1)).isZero();
        assertThat(table.tranCapacity()).isEqualTo(10);
    }

    @Test
    void batStm01_referenceBeyondTheWholeTableIsRefused() {
        StatementTable table = new StatementTable(2, 10);
        assertThatThrownBy(() -> table.putCard(3, "3333333333333333")).isInstanceOf(StatementTableOverflowException.class);
        assertThatThrownBy(() -> table.putTransaction(2, 11, "X", "Y")).isInstanceOf(StatementTableOverflowException.class);
    }
}
