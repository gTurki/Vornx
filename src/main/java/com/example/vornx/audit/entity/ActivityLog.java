package com.example.vornx.audit.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.common.enums.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One line of the audit trail. Immutable: no setters, nothing is ever updated or deleted. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "activity_logs")
public class ActivityLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20, updatable = false)
    private ActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_name", nullable = false, length = 150, updatable = false)
    private String actorName;

    @Column(name = "partner_id", updatable = false)
    private Long partnerId;

    @Column(nullable = false, length = 60, updatable = false)
    private String action;

    @Column(name = "target_type", length = 40, updatable = false)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Column(length = 500, updatable = false)
    private String details;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    public ActivityLog(ActorType actorType, Long actorId, String actorName, Long partnerId, String action,
                       String targetType, Long targetId, String details, String ipAddress) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorName = actorName;
        this.partnerId = partnerId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details != null && details.length() > 500 ? details.substring(0, 499) + "…" : details;
        this.ipAddress = ipAddress;
    }
}
