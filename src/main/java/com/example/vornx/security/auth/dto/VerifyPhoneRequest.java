package com.example.vornx.security.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** firebaseIdToken = user.getIdToken() after Firebase confirmed the SMS code. */
public record VerifyPhoneRequest(
        @NotBlank @Size(max = 100) String challengeToken,
        @NotBlank @Size(max = 4096) String firebaseIdToken
) {
}
