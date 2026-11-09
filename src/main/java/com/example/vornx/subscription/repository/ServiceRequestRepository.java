package com.example.vornx.subscription.repository;

import com.example.vornx.subscription.entity.ServiceRequest;
import com.example.vornx.subscription.enums.RequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    /**
     * Locked: if two staff members press "approve" at the same moment, the second one waits,
     * then sees the request is no longer PENDING. It can never be approved twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ServiceRequest r WHERE r.id = :id AND r.partner.id = :partnerId")
    Optional<ServiceRequest> lockForPartner(@Param("id") Long id, @Param("partnerId") Long partnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ServiceRequest r WHERE r.id = :id AND r.customer.id = :customerId")
    Optional<ServiceRequest> lockForCustomer(@Param("id") Long id, @Param("customerId") Long customerId);

    @EntityGraph(attributePaths = {"customer", "subscription", "subscription.plan"})
    Page<ServiceRequest> findByPartner_IdAndStatus(Long partnerId, RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "subscription", "subscription.plan"})
    Page<ServiceRequest> findByPartner_Id(Long partnerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "subscription", "subscription.plan"})
    List<ServiceRequest> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    Optional<ServiceRequest> findFirstBySubscription_IdAndStatus(Long subscriptionId, RequestStatus status);

    boolean existsBySubscription_IdAndStatus(Long subscriptionId, RequestStatus status);

    boolean existsByRequestNumber(String requestNumber);
}
