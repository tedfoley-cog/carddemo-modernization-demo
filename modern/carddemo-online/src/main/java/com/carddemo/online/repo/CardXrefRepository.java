package com.carddemo.online.repo;

import com.carddemo.domain.model.CardXref;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** CARDXREF (+ AIX CXACAIX on XREF-ACCT-ID). */
public interface CardXrefRepository extends JpaRepository<CardXref, String> {
    /** READ DATASET(CXACAIX) RIDFLD(acct): first xref record for the account in key order. */
    Optional<CardXref> findFirstByAccountIdOrderByCardNumberAsc(Long acctId);
}
