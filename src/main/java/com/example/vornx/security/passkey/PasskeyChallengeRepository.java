package com.example.vornx.security.passkey;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasskeyChallengeRepository extends JpaRepository<PasskeyChallenge, Long> {

    /** Locked: two requests with the same challenge can't both finish. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasskeyChallenge c where c.tokenHash = :hash")
    Optional<PasskeyChallenge> lockByHash(@Param("hash") String hash);

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant after);

    @Modifying
    @Query("delete from PasskeyChallenge c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
