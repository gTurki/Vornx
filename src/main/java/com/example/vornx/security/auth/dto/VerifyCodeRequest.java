package com.example.vornx.security.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyCodeRequest(
        @NotBlank @Size(max = 100) String challengeToken,
        @NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*", message = "must be 6 digits") String code
) {
}
