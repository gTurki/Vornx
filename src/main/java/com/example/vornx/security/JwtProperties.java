package com.example.vornx.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Reads vornx.jwt.* - the app refuses to start if the secret is missing.
 *
 * @param expiration        how long an ACCESS token (JWT) works. Short: it cannot be cancelled.
 * @param refreshExpiration how long a session stays alive without being used. Each refresh resets it.
 */
@Validated
@ConfigurationProperties(prefix = "vornx.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration expiration,
        @NotNull Duration refreshExpiration
) {
}
