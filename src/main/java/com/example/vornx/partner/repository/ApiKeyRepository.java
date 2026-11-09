package com.example.vornx.partner.repository;

import com.example.vornx.partner.entity.ApiKey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    @EntityGraph(attributePaths = "partner")
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByPartner_IdOrderByCreatedAtDesc(Long partnerId);

    Optional<ApiKey> findByIdAndPartner_Id(Long id, Long partnerId);
}
