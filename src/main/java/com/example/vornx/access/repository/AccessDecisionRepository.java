package com.example.vornx.access.repository;

import com.example.vornx.access.entity.AccessDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessDecisionRepository extends JpaRepository<AccessDecision, Long> {

    /** Customer app -> "سجل الدخول والخروج" */
    @EntityGraph(attributePaths = {"accessPoint", "accessPoint.site", "customer", "permit"})
    Page<AccessDecision> findByCustomer_Id(Long customerId, Pageable pageable);

    /** Partner portal -> live events for all their sites */
    @EntityGraph(attributePaths = {"accessPoint", "accessPoint.site", "customer", "permit"})
    Page<AccessDecision> findByAccessPoint_Site_Partner_Id(Long partnerId, Pageable pageable);

    /** Partner portal -> live events for one site */
    @EntityGraph(attributePaths = {"accessPoint", "accessPoint.site", "customer", "permit"})
    Page<AccessDecision> findByAccessPoint_Site_IdAndAccessPoint_Site_Partner_Id(Long siteId, Long partnerId,
                                                                                 Pageable pageable);

    Optional<AccessDecision> findByDecisionNumberAndAccessPoint_Site_Partner_Id(String decisionNumber, Long partnerId);
}
