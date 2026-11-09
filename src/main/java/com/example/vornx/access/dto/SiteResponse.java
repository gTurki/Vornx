package com.example.vornx.access.dto;

import com.example.vornx.access.entity.Site;
import com.example.vornx.access.enums.SiteType;

import java.time.LocalTime;

public record SiteResponse(
        Long id,
        String name,
        SiteType type,
        String address,
        String city,
        String country,
        Double latitude,
        Double longitude,
        LocalTime opensAt,
        LocalTime closesAt,
        boolean open24Hours
) {
    public static SiteResponse from(Site s) {
        return new SiteResponse(s.getId(), s.getName(), s.getType(), s.getAddress(), s.getCity(), s.getCountry(),
                s.getLatitude(), s.getLongitude(), s.getOpensAt(), s.getClosesAt(),
                s.getOpensAt() == null && s.getClosesAt() == null);
    }
}
