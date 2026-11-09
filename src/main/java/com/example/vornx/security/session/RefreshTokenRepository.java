package com.example.vornx.security.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Locked: two refreshes with the same token at the same moment are handled one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.session WHERE t.tokenHash = :hash")
    Optional<RefreshToken> lockByHash(@Param("hash") String hash);

    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.session WHERE t.tokenHash = :hash")
    Optional<RefreshToken> findByHash(@Param("hash") String hash);
}
