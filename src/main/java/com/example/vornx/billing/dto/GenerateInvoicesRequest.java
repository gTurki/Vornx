package com.example.vornx.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {"period": "2026-11"} */
public record GenerateInvoicesRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Period must look like 2026-11") String period
) {
}
