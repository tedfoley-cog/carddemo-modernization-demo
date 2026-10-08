package com.carddemo.online.repo;

import com.carddemo.domain.model.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** ACCTDATA. */
public interface AccountRepository extends JpaRepository<Account, Long> {
    /** EXEC CICS READ ... UPDATE equivalent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findForUpdate(Long id);
}
