package com.carddemo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Transaction category reference data (copybook CVTRA04Y, VSAM TRANCATG). */
@Entity
@Table(name = "transaction_category")
public class TransactionCategory extends AssignedIdEntity<TransactionCategoryId> {

    @EmbeddedId
    private TransactionCategoryId id;

    @Column(name = "description", length = 50)
    private String description;

    @Override
    public TransactionCategoryId getId() { return id; }
    public void setId(TransactionCategoryId id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
