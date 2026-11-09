package com.example.vornx.customer.dto;

import com.example.vornx.common.enums.Language;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/public/auth/customer/register (the "Create account" form in index_76). */
public record CustomerRegisterRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = "^05\\d{8}$", message = "Mobile format: 05XXXXXXXX") String phone,
        @NotBlank @Size(min = 10, max = 100, message = "Password must be at least 10 characters") String password,
        @NotBlank String confirmPassword,
        Language language
) {
}
