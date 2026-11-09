package com.example.vornx.security.session;

import com.example.vornx.security.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    @Query("""
            SELECT s FROM AuthSession s
            WHERE s.userType = :type AND s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now
            ORDER BY s.lastUsedAt DESC
            """)
    List<AuthSession> findActive(@Param("type") UserType type, @Param("userId") Long userId, @Param("now") Instant now);

    Optional<AuthSession> findByIdAndUserTypeAndUserId(Long id, UserType userType, Long userId);

    /** "Log out all devices" - one UPDATE. */
    @Modifying
    @Query("""
            UPDATE AuthSession s SET s.revokedAt = :now, s.revokeReason = :reason
            WHERE s.userType = :type AND s.userId = :userId AND s.revokedAt IS NULL
              AND (:exceptId IS NULL OR s.id <> :exceptId)
            """)
    int revokeAll(@Param("type") UserType type, @Param("userId") Long userId, @Param("exceptId") Long exceptId,
                  @Param("now") Instant now, @Param("reason") String reason);

    /** Housekeeping: sessions that ended more than a month ago. Their refresh tokens go too (ON DELETE CASCADE). */
    @Modifying
    @Query("DELETE FROM AuthSession s WHERE s.expiresAt < :before OR s.revokedAt < :before")
    int deleteEndedBefore(@Param("before") Instant before);
}
