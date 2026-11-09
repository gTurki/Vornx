package com.example.vornx.access.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** "تمديد الإقامة" - extend a hotel stay, a membership... */
public record PermitExtendRequest(@NotNull Instant validUntil) {
}
