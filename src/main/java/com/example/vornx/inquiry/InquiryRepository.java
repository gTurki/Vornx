package com.example.vornx.inquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant after);

    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, Instant after);

    @Query("SELECT i FROM Inquiry i WHERE (:status IS NULL OR i.status = :status)")
    Page<Inquiry> search(@Param("status") Inquiry.Status status, Pageable pageable);

    long countByStatus(Inquiry.Status status);
}
