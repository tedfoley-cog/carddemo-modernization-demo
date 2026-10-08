package com.carddemo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Running balance per account, transaction type and category (copybook CVTRA01Y, VSAM TCATBALF). */
@Entity
@Table(name = "transaction_category_balance")
public class TransactionCategoryBalance extends AssignedIdEntity<CategoryBalanceId> {

    @EmbeddedId
    private CategoryBalanceId id;

    @Column(name = "balance", precision = 11, scale = 2, nullable = false)
    private BigDecimal balance;

    protected TransactionCategoryBalance() {
    }

    public TransactionCategoryBalance(CategoryBalanceId id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }

    @Override
    public CategoryBalanceId getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
