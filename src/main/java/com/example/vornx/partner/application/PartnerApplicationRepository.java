package com.example.vornx.partner.application;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PartnerApplicationRepository extends JpaRepository<PartnerApplication, Long> {

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant after);

    long countByContactEmailIgnoreCaseAndCreatedAtAfter(String email, Instant after);

    boolean existsByCrNumberAndStatus(String crNumber, PartnerApplication.Status status);

    @EntityGraph(attributePaths = "reviewedBy")
    @Query("SELECT a FROM PartnerApplication a WHERE (:status IS NULL OR a.status = :status)")
    Page<PartnerApplication> search(@Param("status") PartnerApplication.Status status, Pageable pageable);

    long countByStatus(PartnerApplication.Status status);

    /** Row lock: two admins pressing "approve" at the same time can't create the partner twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PartnerApplication a WHERE a.id = :id")
    Optional<PartnerApplication> lockById(@Param("id") Long id);
}
