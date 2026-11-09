package com.example.vornx.subscription.repository;

import com.example.vornx.subscription.entity.Subscription;
import com.example.vornx.subscription.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @EntityGraph(attributePaths = {"plan", "plan.site", "partner", "permit"})
    List<Subscription> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"plan", "plan.site", "partner", "permit"})
    Optional<Subscription> findByIdAndCustomer_Id(Long id, Long customerId);

    @EntityGraph(attributePaths = {"plan", "plan.site", "partner", "permit", "customer"})
    Page<Subscription> findByPartner_Id(Long partnerId, Pageable pageable);

    boolean existsByCustomer_IdAndPlan_IdAndStatusIn(Long customerId, Long planId, Collection<SubscriptionStatus> statuses);

    boolean existsByNumber(String number);
}
