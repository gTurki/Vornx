package com.example.vornx.billing.repository;

import com.example.vornx.billing.entity.Invoice;
import com.example.vornx.billing.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"partner", "contract"})
    Page<Invoice> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"partner", "contract"})
    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    /** Overdue = still DUE and the due date has passed. */
    @EntityGraph(attributePaths = {"partner", "contract"})
    Page<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate today, Pageable pageable);

    @EntityGraph(attributePaths = {"partner", "contract"})
    List<Invoice> findByPartner_IdOrderByPeriodDesc(Long partnerId);

    @EntityGraph(attributePaths = {"partner", "contract"})
    Optional<Invoice> findWithPartnerById(Long id);

    boolean existsByContract_IdAndPeriod(Long contractId, String period);

    boolean existsByNumber(String number);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status = :status AND i.dueDate < :today")
    long countOverdue(@Param("status") InvoiceStatus status, @Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.status = :status AND i.dueDate < :today")
    BigDecimal sumOverdue(@Param("status") InvoiceStatus status, @Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.status = :status")
    BigDecimal sumByStatus(@Param("status") InvoiceStatus status);
}
