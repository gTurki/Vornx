package com.example.vornx.security.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendCodeRequest(@NotBlank @Size(max = 100) String challengeToken) {
}
