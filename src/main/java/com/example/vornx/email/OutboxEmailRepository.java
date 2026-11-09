package com.example.vornx.email;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEmailRepository extends JpaRepository<OutboxEmail, Long> {

    @Query("SELECT e FROM OutboxEmail e WHERE e.status = :status AND e.nextAttemptAt <= :now ORDER BY e.id")
    List<OutboxEmail> findDue(@Param("status") OutboxEmail.Status status, @Param("now") Instant now, Pageable limit);
}
