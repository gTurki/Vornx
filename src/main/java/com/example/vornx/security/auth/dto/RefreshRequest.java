package com.example.vornx.security.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/public/auth/refresh and /logout */
public record RefreshRequest(@NotBlank String refreshToken) {
}
