package com.example.vornx.events.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Example:
 * {"siteId":1,"title":"Tech Conference 2026","category":"Conference",
 *  "startsAt":"2026-12-18T15:00:00Z","endsAt":"2026-12-18T19:00:00Z","fee":0,"capacity":500,
 *  "agenda":[{"time":"18:00","title":"Opening"},{"time":"19:30","title":"Keynote"}]}
 */
public record EventRequest(
        @NotNull Long siteId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @Size(max = 80) String category,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        Instant doorsOpenAt,
        @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal fee, // default 0 = free
        @Min(1) Integer capacity,                                              // null = unlimited
        Boolean requiresApproval,                                              // default false
        @Size(max = 100) String dressCode,
        @Size(max = 20) String contactPhone,
        Set<Long> accessPointIds,                                              // empty = whole site
        @Valid List<AgendaItemDto> agenda
) {
    public record AgendaItemDto(@NotNull LocalTime time, @NotBlank @Size(max = 200) String title) {
    }
}
