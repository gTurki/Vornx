package com.example.vornx.partner.repository;

import com.example.vornx.partner.entity.Partner;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.enums.Sector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    /** Every filter is optional: when a parameter is null, that condition is ignored. */
    @EntityGraph(attributePaths = "accountManager")
    @Query("""
            SELECT p FROM Partner p
            WHERE (:sector IS NULL OR p.sector = :sector)
              AND (:status IS NULL OR p.status = :status)
              AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Partner> search(@Param("sector") Sector sector,
                         @Param("status") PartnerStatus status,
                         @Param("q") String q,
                         Pageable pageable);

    @EntityGraph(attributePaths = "accountManager")
    Optional<Partner> findWithAccountManagerById(Long id);

    long countByStatus(PartnerStatus status);

    boolean existsByCrNumber(String crNumber);

    boolean existsByCrNumberAndIdNot(String crNumber, Long id);
}
