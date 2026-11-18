package com.example.vornx.platform.repository;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.enums.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<AdminUser> findByInviteTokenHash(String inviteTokenHash);

    /** "فريق VORNX": everyone, oldest first (the first SUPER_ADMIN on top). */
    List<AdminUser> findAllByOrderByCreatedAtAsc();

    /** Guards "there must always be at least one active SUPER_ADMIN". */
    long countByRoleAndStatus(AdminRole role, AccountStatus status);
}
