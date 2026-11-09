package com.example.vornx.access.repository;

import com.example.vornx.access.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findByPartner_IdOrderByName(Long partnerId);

    /** Always look a site up WITH the partner id -> a partner can never open another partner's site. */
    Optional<Site> findByIdAndPartner_Id(Long id, Long partnerId);
}
