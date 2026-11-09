package com.example.vornx.partner.dto;

import com.example.vornx.partner.entity.ApiKey;

import java.time.Instant;

/** A key in the list. Never contains the key itself. */
public record ApiKeyResponse(
        Long id,
        String name,
        String keyPrefix,
        boolean active,
        Instant lastUsedAt,
        Instant createdAt
) {
    public static ApiKeyResponse from(ApiKey k) {
        return new ApiKeyResponse(k.getId(), k.getName(), k.getKeyPrefix(), k.isActive(), k.getLastUsedAt(),
                k.getCreatedAt());
    }
}
