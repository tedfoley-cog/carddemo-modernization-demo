package com.carddemo.online.repo;

import com.carddemo.domain.model.CardTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** TRANSACT. */
public interface TransactionRepository extends JpaRepository<CardTransaction, String> {
    List<CardTransaction> findByTransactionIdGreaterThanEqualOrderByTransactionIdAsc(String from, Pageable page);

    List<CardTransaction> findByTransactionIdGreaterThanOrderByTransactionIdAsc(String from, Pageable page);

    List<CardTransaction> findByTransactionIdLessThanOrderByTransactionIdDesc(String from, Pageable page);

    /** STARTBR RIDFLD(HIGH-VALUES) + READPREV: the last (highest) key. */
    Optional<CardTransaction> findFirstByOrderByTransactionIdDesc();

    @Query("select count(t) from CardTransaction t")
    long countAll();
}
