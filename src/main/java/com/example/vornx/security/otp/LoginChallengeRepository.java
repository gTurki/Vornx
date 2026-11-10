package com.example.vornx.security.otp;

import com.example.vornx.security.UserType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, Long> {

    /** Row lock: two parallel guesses can't both read "attempts = 4" and each get a 5th try. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM LoginChallenge c WHERE c.tokenHash = :hash")
    Optional<LoginChallenge> lockByHash(@Param("hash") String hash);

    long countByUserTypeAndUserIdAndCreatedAtAfter(UserType userType, Long userId, Instant after);

    /** A new login replaces the earlier unfinished ones: only the newest code works. */
    @Modifying
    @Query("""
            UPDATE LoginChallenge c SET c.consumedAt = :now
            WHERE c.userType = :type AND c.userId = :userId AND c.consumedAt IS NULL
            """)
    int consumeOpen(@Param("type") UserType type, @Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM LoginChallenge c WHERE c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
