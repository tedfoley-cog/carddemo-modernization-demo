package com.carddemo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Transaction type reference data (copybook CVTRA03Y, VSAM TRANTYPE). */
@Entity
@Table(name = "transaction_type")
public class TransactionType extends AssignedIdEntity<String> {

    @Id
    @Column(name = "type_code", length = 2)
    private String typeCode;

    @Column(name = "description", length = 50)
    private String description;

    @Override
    public String getId() { return typeCode; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
