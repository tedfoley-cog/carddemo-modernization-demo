package com.carddemo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Annual interest rate by account group / transaction type / category (copybook CVTRA02Y, VSAM DISCGRP). */
@Entity
@Table(name = "disclosure_group")
public class DisclosureGroup extends AssignedIdEntity<DisclosureGroupId> {

    @EmbeddedId
    private DisclosureGroupId id;

    @Column(name = "interest_rate", precision = 6, scale = 2, nullable = false)
    private BigDecimal interestRate;

    @Override
    public DisclosureGroupId getId() { return id; }
    public void setId(DisclosureGroupId id) { this.id = id; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
}
