package com.carddemo.online.repo;

import com.carddemo.online.domain.Transaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** TRANSACT. */
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByTranIdGreaterThanEqualOrderByTranIdAsc(String from, Pageable page);

    List<Transaction> findByTranIdGreaterThanOrderByTranIdAsc(String from, Pageable page);

    List<Transaction> findByTranIdLessThanOrderByTranIdDesc(String from, Pageable page);

    /** STARTBR RIDFLD(HIGH-VALUES) + READPREV: the last (highest) key. */
    Optional<Transaction> findFirstByOrderByTranIdDesc();

    @Query("select count(t) from Transaction t")
    long countAll();
}
