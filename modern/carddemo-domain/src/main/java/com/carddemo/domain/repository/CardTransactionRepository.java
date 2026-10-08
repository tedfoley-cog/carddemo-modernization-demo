package com.carddemo.domain.repository;

import com.carddemo.domain.model.CardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, String> {
}
