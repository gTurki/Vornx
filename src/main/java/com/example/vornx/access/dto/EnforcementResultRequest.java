package com.example.vornx.access.dto;

import com.example.vornx.access.enums.EnforcementResult;
import jakarta.validation.constraints.NotNull;

/** The reader reports back whether the door really opened after an ALLOW. */
public record EnforcementResultRequest(@NotNull EnforcementResult result) {
}
