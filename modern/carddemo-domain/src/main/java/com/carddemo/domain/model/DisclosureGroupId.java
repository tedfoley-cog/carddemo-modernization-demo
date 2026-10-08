package com.carddemo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/** Key of a disclosure group rate: account group, transaction type and category. */
@Embeddable
public record DisclosureGroupId(
        @Column(name = "account_group_id", length = 10) String accountGroupId,
        @Column(name = "transaction_type_code", length = 2) String transactionTypeCode,
        @Column(name = "transaction_category_code") Integer transactionCategoryCode) implements Serializable {

    /** Group used when an account's own group has no rate row (CBACT04C 1200-A-GET-DEFAULT-INT-RATE). */
    public static final String DEFAULT_GROUP = "DEFAULT";
}
