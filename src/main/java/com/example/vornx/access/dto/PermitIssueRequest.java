package com.example.vornx.access.dto;

import com.example.vornx.access.enums.AccessLevel;
import com.example.vornx.access.enums.PermitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

/**
 * Partner gives a customer access. Example - hotel key for room 1204:
 * {
 *   "customerVornxId": "VX0009668551", "type": "HOTEL_KEY", "siteId": 1, "accessPointIds": [1],
 *   "validFrom": "2026-11-09T12:00:00Z", "validUntil": "2026-11-12T09:00:00Z"
 * }
 * Example - office access Sun-Thu 08:00-17:00:
 *   ..."allowedDays": ["SUNDAY","MONDAY","TUESDAY","WEDNESDAY","THURSDAY"], "allowedFrom": "08:00", "allowedTo": "17:00"
 */
public record PermitIssueRequest(
        @NotBlank String customerVornxId,
        @NotNull PermitType type,
        @NotNull Long siteId,
        Set<Long> accessPointIds,   // empty = the whole site
        AccessLevel level,          // default STANDARD
        Instant validFrom,          // default: now
        @NotNull Instant validUntil,
        Set<DayOfWeek> allowedDays, // empty = every day
        LocalTime allowedFrom,      // both empty = any time
        LocalTime allowedTo,
        @Size(max = 500) String conditions
) {
}
