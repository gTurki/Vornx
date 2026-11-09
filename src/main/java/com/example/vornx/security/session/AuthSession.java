package com.example.vornx.security.session;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.security.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One login on one device. Shown in "my devices"; revoking it logs that device out. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auth_sessions")
public class AuthSession extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20, updatable = false)
    private UserType userType;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 30)
    private String revokeReason;

    public AuthSession(UserType userType, Long userId, String userAgent, String ipAddress, Instant now,
                       Instant expiresAt) {
        this.userType = userType;
        this.userId = userId;
        this.userAgent = userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
        this.ipAddress = ipAddress;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void touch(Instant now, Instant newExpiry, String ip) {
        this.lastUsedAt = now;
        this.expiresAt = newExpiry;
        if (ip != null) {
            this.ipAddress = ip;
        }
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        }
    }
}
