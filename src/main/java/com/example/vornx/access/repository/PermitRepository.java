package com.example.vornx.access.repository;

import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.entity.Permit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermitRepository extends JpaRepository<Permit, Long> {

    /**
     * The permits that COULD open this access point for this customer:
     * same site, and either the permit covers the whole site or lists this access point.
     * (Dates, days and hours are then checked in Java by PermitRules.)
     */
    @EntityGraph(attributePaths = "allowedDays")
    @Query("""
            SELECT p FROM Permit p
            WHERE p.customer.id = :customerId
              AND p.site.id = :siteId
              AND (p.accessPoints IS EMPTY OR :accessPoint MEMBER OF p.accessPoints)
            """)
    List<Permit> findCandidates(@Param("customerId") Long customerId,
                                @Param("siteId") Long siteId,
                                @Param("accessPoint") AccessPoint accessPoint);

    @EntityGraph(attributePaths = {"site", "customer"})
    Page<Permit> findByPartner_Id(Long partnerId, Pageable pageable);

    @EntityGraph(attributePaths = {"site", "customer"})
    Page<Permit> findByPartner_IdAndSite_Id(Long partnerId, Long siteId, Pageable pageable);

    @EntityGraph(attributePaths = {"site", "customer"})
    Optional<Permit> findByIdAndPartner_Id(Long id, Long partnerId);

    @EntityGraph(attributePaths = {"site", "partner"})
    List<Permit> findByCustomer_IdOrderByValidUntilDesc(Long customerId);

    @EntityGraph(attributePaths = {"site", "partner"})
    Optional<Permit> findByIdAndCustomer_Id(Long id, Long customerId);

    boolean existsByPermitNumber(String permitNumber);
}
