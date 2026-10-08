package com.carddemo.online.repo;

import com.carddemo.domain.model.UserSecurity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** USRSEC. */
public interface UserSecurityRepository extends JpaRepository<UserSecurity, String> {
    List<UserSecurity> findByUserIdGreaterThanEqualOrderByUserIdAsc(String from, Pageable page);

    List<UserSecurity> findByUserIdGreaterThanOrderByUserIdAsc(String from, Pageable page);

    List<UserSecurity> findByUserIdLessThanOrderByUserIdDesc(String from, Pageable page);
}
