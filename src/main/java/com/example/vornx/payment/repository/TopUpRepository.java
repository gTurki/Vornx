package com.example.vornx.payment.repository;

import com.example.vornx.payment.entity.TopUp;
import com.example.vornx.payment.enums.TopUpStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TopUpRepository extends JpaRepository<TopUp, Long> {

    /** Locks the row: the return page, Moyasar's callback and the job may check the same top-up at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TopUp t WHERE t.id = :id")
    Optional<TopUp> lockById(@Param("id") Long id);

    Optional<TopUp> findByReferenceAndCustomer_Id(String reference, Long customerId);

    Optional<TopUp> findByProviderInvoiceId(String providerInvoiceId);

    boolean existsByReference(String reference);

    long countByCustomer_IdAndStatusAndCreatedAtAfter(Long customerId, TopUpStatus status, Instant after);

    @Query("SELECT t.id FROM TopUp t WHERE t.status = :status AND t.createdAt < :before ORDER BY t.createdAt")
    List<Long> findIdsByStatusCreatedBefore(@Param("status") TopUpStatus status, @Param("before") Instant before, Pageable pageable);
}
