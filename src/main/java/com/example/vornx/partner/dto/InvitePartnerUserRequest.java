package com.example.vornx.partner.dto;

import com.example.vornx.partner.enums.PartnerRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "ادعُ موظفًا" - invite a staff member by email. */
public record InvitePartnerUserRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 120) String email,
        @NotNull PartnerRole role
) {
}
