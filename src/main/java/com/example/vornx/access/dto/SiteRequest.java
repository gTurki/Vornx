package com.example.vornx.access.dto;

import com.example.vornx.access.enums.SiteType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** Body for creating/updating a site. Times are "HH:mm", e.g. "05:00". Leave both empty for 24h. */
public record SiteRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull SiteType type,
        @Size(max = 255) String address,
        @Size(max = 80) String city,
        @Size(max = 80) String country,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        LocalTime opensAt,
        LocalTime closesAt
) {
}
