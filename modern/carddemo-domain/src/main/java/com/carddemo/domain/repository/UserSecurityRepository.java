package com.carddemo.domain.repository;

import com.carddemo.domain.model.UserSecurity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSecurityRepository extends JpaRepository<UserSecurity, String> {
}
