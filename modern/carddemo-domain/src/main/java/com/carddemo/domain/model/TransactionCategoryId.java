package com.carddemo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record TransactionCategoryId(
        @Column(name = "type_code", length = 2) String typeCode,
        @Column(name = "category_code") Integer categoryCode) implements Serializable {
}
