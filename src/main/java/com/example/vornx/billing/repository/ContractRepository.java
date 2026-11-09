package com.example.vornx.billing.repository;

import com.example.vornx.billing.entity.Contract;
import com.example.vornx.billing.enums.ContractStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    @EntityGraph(attributePaths = "partner")
    List<Contract> findByStatusOrderByEndDateAsc(ContractStatus status);

    @EntityGraph(attributePaths = "partner")
    List<Contract> findByPartner_IdOrderByStartDateDesc(Long partnerId);

    /** The partner's current contract (not terminated), newest first. */
    @EntityGraph(attributePaths = "partner")
    Optional<Contract> findFirstByPartner_IdAndStatusOrderByEndDateDesc(Long partnerId, ContractStatus status);

    @EntityGraph(attributePaths = "partner")
    Optional<Contract> findWithPartnerById(Long id);

    boolean existsByPartner_IdAndStatusAndEndDateGreaterThanEqual(Long partnerId, ContractStatus status, LocalDate date);

    /** Contracts that cover at least one day of the month [from, to]. */
    @EntityGraph(attributePaths = "partner")
    @Query("""
            SELECT c FROM Contract c
            WHERE c.status = :status AND c.startDate <= :to AND c.endDate >= :from
            """)
    List<Contract> findActiveDuring(@Param("status") ContractStatus status,
                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** MRR = sum of monthly fees of contracts that have not ended. */
    @Query("SELECT COALESCE(SUM(c.monthlyFee), 0) FROM Contract c WHERE c.status = :status AND c.endDate >= :today")
    BigDecimal sumMonthlyFees(@Param("status") ContractStatus status, @Param("today") LocalDate today);

    @Query("SELECT COUNT(c) FROM Contract c WHERE c.status = :status AND c.endDate >= :today")
    long countRunning(@Param("status") ContractStatus status, @Param("today") LocalDate today);

    /** Ending within the warning window, or already ended: "تحتاج متابعة". */
    @Query("SELECT COUNT(c) FROM Contract c WHERE c.status = :status AND c.endDate <= :warnDate")
    long countNeedingAttention(@Param("status") ContractStatus status, @Param("warnDate") LocalDate warnDate);

    boolean existsByNumber(String number);
}
