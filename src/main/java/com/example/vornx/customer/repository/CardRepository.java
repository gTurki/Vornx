package com.example.vornx.customer.repository;

import com.example.vornx.customer.entity.Card;
import com.example.vornx.customer.enums.CardStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    /** Used on every tap at a reader, so it also loads the customer in the same query. */
    @EntityGraph(attributePaths = "customer")
    Optional<Card> findByCardNumber(String cardNumber);

    List<Card> findByCustomer_IdOrderByIssuedAtDescIdDesc(Long customerId);

    Optional<Card> findByIdAndCustomer_Id(Long id, Long customerId);

    boolean existsByCustomer_IdAndStatus(Long customerId, CardStatus status);

    boolean existsByCardNumber(String cardNumber);
}
