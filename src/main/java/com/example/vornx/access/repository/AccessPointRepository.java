package com.example.vornx.access.repository;

import com.example.vornx.access.entity.AccessPoint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessPointRepository extends JpaRepository<AccessPoint, Long> {

    /** Loads the supported methods in the same query (no "N+1 queries" problem). */
    @EntityGraph(attributePaths = "supportedMethods")
    List<AccessPoint> findBySite_IdOrderByCode(Long siteId);

    /** WHERE id = ? AND site.partner.id = ?  -> tenant isolation again. */
    @EntityGraph(attributePaths = {"supportedMethods", "site"})
    Optional<AccessPoint> findByIdAndSite_Partner_Id(Long id, Long partnerId);

    /** Remote open from the customer app: any partner's access point (the permit check decides). */
    @EntityGraph(attributePaths = {"supportedMethods", "site"})
    Optional<AccessPoint> findWithSiteById(Long id);

    boolean existsBySite_IdAndCode(Long siteId, String code);

    boolean existsBySite_IdAndCodeAndIdNot(Long siteId, String code, Long id);
}
