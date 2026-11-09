package com.example.vornx.security.password;

import com.example.vornx.security.UserType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :hash")
    Optional<PasswordResetToken> lockByHash(@Param("hash") String hash);

    boolean existsByUserTypeAndUserIdAndCreatedAtAfter(UserType userType, Long userId, Instant after);

    /** After a successful reset, older links for the same user stop working too. */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t SET t.usedAt = :now
            WHERE t.userType = :type AND t.userId = :userId AND t.usedAt IS NULL
            """)
    int invalidateAll(@Param("type") UserType type, @Param("userId") Long userId, @Param("now") Instant now);
}
