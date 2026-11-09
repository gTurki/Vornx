package com.example.vornx.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** e.g. {"name": "Readers - InterContinental Riyadh"} */
public record ApiKeyRequest(@NotBlank @Size(max = 100) String name) {
}
