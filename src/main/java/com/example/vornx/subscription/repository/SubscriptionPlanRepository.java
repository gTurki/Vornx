package com.example.vornx.subscription.repository;

import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.subscription.entity.SubscriptionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    @EntityGraph(attributePaths = {"site", "partner"})
    List<SubscriptionPlan> findByPartner_IdOrderByName(Long partnerId);

    @EntityGraph(attributePaths = {"site", "partner"})
    Optional<SubscriptionPlan> findByIdAndPartner_Id(Long id, Long partnerId);

    @EntityGraph(attributePaths = {"site", "partner"})
    Optional<SubscriptionPlan> findWithSiteAndPartnerById(Long id);

    /** Customer catalog: active plans of active partners, optionally filtered. */
    @EntityGraph(attributePaths = {"site", "partner"})
    @Query("""
            SELECT p FROM SubscriptionPlan p
            WHERE p.active = true
              AND p.partner.status = :partnerStatus
              AND (:partnerId IS NULL OR p.partner.id = :partnerId)
              AND (:siteId IS NULL OR p.site.id = :siteId)
            """)
    Page<SubscriptionPlan> catalog(@Param("partnerStatus") PartnerStatus partnerStatus,
                                   @Param("partnerId") Long partnerId,
                                   @Param("siteId") Long siteId,
                                   Pageable pageable);
}
