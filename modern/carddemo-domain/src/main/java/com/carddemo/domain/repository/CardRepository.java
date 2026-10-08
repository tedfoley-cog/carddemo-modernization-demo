package com.carddemo.domain.repository;

import com.carddemo.domain.model.Card;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, String> {

    List<Card> findByAccountIdOrderByCardNumber(Long accountId);
}
