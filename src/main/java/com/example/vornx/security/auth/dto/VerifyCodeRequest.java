package com.example.vornx.security.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** code: 6 digits (email, WhatsApp or authenticator app), or a recovery code like "ABCDE-FGH23". */
public record VerifyCodeRequest(
        @NotBlank @Size(max = 100) String challengeToken,
        @NotBlank @Pattern(regexp = "\\s*(\\d{6}|[A-Za-z0-9]{5}-?[A-Za-z0-9]{5})\\s*",
                message = "must be 6 digits or a recovery code") String code
) {
}
