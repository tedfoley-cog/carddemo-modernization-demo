package com.carddemo.online.repo;

import com.carddemo.online.domain.Card;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** CARDDATA (+ AIX CARDAIX on CARD-ACCT-ID). */
public interface CardRepository extends JpaRepository<Card, String> {
    List<Card> findByAcctId(Long acctId);

    /** STARTBR GTEQ / READNEXT over CARDDATA with the COCRDLIC 9500-FILTER-RECORDS predicate. */
    @Query("""
            select c from Card c
             where c.cardNum >= :from
               and (:acctId is null or c.acctId = :acctId)
               and (:cardNum is null or c.cardNum = :cardNum)
             order by c.cardNum asc""")
    List<Card> browseForward(String from, Long acctId, String cardNum, Pageable page);

    /** STARTBR / READPREV. */
    @Query("""
            select c from Card c
             where c.cardNum <= :from
               and (:acctId is null or c.acctId = :acctId)
               and (:cardNum is null or c.cardNum = :cardNum)
             order by c.cardNum desc""")
    List<Card> browseBackward(String from, Long acctId, String cardNum, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c where c.cardNum = :cardNum")
    Optional<Card> findForUpdate(String cardNum);
}
