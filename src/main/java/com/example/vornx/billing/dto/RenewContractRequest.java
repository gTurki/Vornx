package com.example.vornx.billing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** "تجديد العقد سنة" = {"months": 12} (the default). */
public record RenewContractRequest(@Min(1) @Max(60) Integer months) {
}
