package com.example.vornx.partner.dto;

import com.example.vornx.partner.enums.PartnerStatus;
import jakarta.validation.constraints.NotNull;

public record PartnerStatusRequest(@NotNull PartnerStatus status) {
}
