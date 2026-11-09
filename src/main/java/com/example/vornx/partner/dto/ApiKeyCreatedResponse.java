package com.example.vornx.partner.dto;

/** Returned ONCE when a key is created. The partner must copy apiKey now; it can never be shown again. */
public record ApiKeyCreatedResponse(
        ApiKeyResponse key,
        String apiKey
) {
}
