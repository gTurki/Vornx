package com.example.vornx.security.session;

import java.time.Instant;

/** One line of "my devices" ("iPhone 15 Pro — هذا الجهاز"). */
public record SessionResponse(
        Long id,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean current // true = the device making this request
) {
    public static SessionResponse from(AuthSession s, Long currentSessionId) {
        return new SessionResponse(s.getId(), s.getUserAgent(), s.getIpAddress(), s.getCreatedAt(), s.getLastUsedAt(),
                s.getExpiresAt(), s.getId().equals(currentSessionId));
    }
}
