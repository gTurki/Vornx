package com.example.vornx.audit.repository;

import com.example.vornx.audit.entity.ActivityLog;
import com.example.vornx.common.enums.ActorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /** Every filter optional. */
    @Query("""
            SELECT a FROM ActivityLog a
            WHERE (:actorType IS NULL OR a.actorType = :actorType)
              AND (:partnerId IS NULL OR a.partnerId = :partnerId)
              AND (:action IS NULL OR a.action = :action)
              AND (:targetType IS NULL OR a.targetType = :targetType)
              AND (:targetId IS NULL OR a.targetId = :targetId)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt < :to)
            """)
    Page<ActivityLog> search(@Param("actorType") ActorType actorType,
                             @Param("partnerId") Long partnerId,
                             @Param("action") String action,
                             @Param("targetType") String targetType,
                             @Param("targetId") Long targetId,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);
}
