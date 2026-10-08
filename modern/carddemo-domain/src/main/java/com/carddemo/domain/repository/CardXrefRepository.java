package com.carddemo.domain.repository;

import com.carddemo.domain.model.CardXref;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXrefRepository extends JpaRepository<CardXref, String> {

    /** Alternate-index read by account (CARDXREF AIX): first card in key order. */
    Optional<CardXref> findFirstByAccountIdOrderByCardNumber(Long accountId);
}
