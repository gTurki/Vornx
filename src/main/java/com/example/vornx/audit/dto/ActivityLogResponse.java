package com.example.vornx.audit.dto;

import com.example.vornx.audit.entity.ActivityLog;
import com.example.vornx.common.enums.ActorType;

import java.time.Instant;

public record ActivityLogResponse(
        Long id,
        Instant at,
        ActorType actorType,
        Long actorId,
        String actorName,
        Long partnerId,
        String action,
        String targetType,
        Long targetId,
        String details,
        String ipAddress
) {
    public static ActivityLogResponse from(ActivityLog a) {
        return new ActivityLogResponse(a.getId(), a.getCreatedAt(), a.getActorType(), a.getActorId(), a.getActorName(),
                a.getPartnerId(), a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetails(), a.getIpAddress());
    }
}
