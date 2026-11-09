package com.example.vornx.security.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for the password endpoints. */
public final class PasswordDtos {

    private PasswordDtos() {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 100, message = "Password must be at least 10 characters") String password,
            @NotBlank String confirmPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 100, message = "Password must be at least 10 characters") String newPassword,
            @NotBlank String confirmPassword) {
    }
}
