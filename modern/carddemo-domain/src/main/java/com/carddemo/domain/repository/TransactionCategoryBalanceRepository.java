package com.carddemo.domain.repository;

import com.carddemo.domain.model.CategoryBalanceId;
import com.carddemo.domain.model.TransactionCategoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCategoryBalanceRepository extends JpaRepository<TransactionCategoryBalance, CategoryBalanceId> {
}
