package com.example.vornx.partner.repository;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.partner.entity.PartnerUser;
import com.example.vornx.partner.enums.PartnerRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerUserRepository extends JpaRepository<PartnerUser, Long> {

    /** @EntityGraph loads the partner in the SAME query (avoids a second query later). */
    @EntityGraph(attributePaths = "partner")
    Optional<PartnerUser> findByEmail(String email);

    @EntityGraph(attributePaths = "partner")
    Optional<PartnerUser> findWithPartnerById(Long id);

    @EntityGraph(attributePaths = "partner")
    Optional<PartnerUser> findByInviteTokenHash(String inviteTokenHash);

    /** "_" means "go into the partner, then its id": WHERE partner.id = ? */
    @EntityGraph(attributePaths = "partner")
    List<PartnerUser> findByPartner_IdOrderByFullName(Long partnerId);

    @EntityGraph(attributePaths = "partner")
    Optional<PartnerUser> findByIdAndPartner_Id(Long id, Long partnerId);

    List<PartnerUser> findByPartner_IdAndRoleAndStatus(Long partnerId, PartnerRole role,
                                                      AccountStatus status);

    boolean existsByEmail(String email);
}
