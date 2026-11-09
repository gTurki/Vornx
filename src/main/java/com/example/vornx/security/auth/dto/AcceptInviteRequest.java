package com.example.vornx.security.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/public/auth/partner/accept-invite */
public record AcceptInviteRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 10, max = 100, message = "Password must be at least 10 characters") String password,
        @NotBlank String confirmPassword
) {
}
