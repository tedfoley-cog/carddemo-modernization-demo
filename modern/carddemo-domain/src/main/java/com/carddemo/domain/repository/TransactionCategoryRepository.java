package com.carddemo.domain.repository;

import com.carddemo.domain.model.TransactionCategory;
import com.carddemo.domain.model.TransactionCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, TransactionCategoryId> {
}
