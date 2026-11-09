package com.example.vornx.partner.dto;

import com.example.vornx.partner.enums.Sector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for creating (POST) or updating (PUT) a partner. */
public record PartnerRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull Sector sector,
        @Pattern(regexp = "^\\d{10}$", message = "CR number must be 10 digits") String crNumber,
        @Size(max = 20) String phone,
        @Size(max = 80) String city,
        @Size(max = 80) String country,
        @Size(max = 2000) String notes,
        Long accountManagerId
) {
}
