package com.example.vornx.partner.dto;

import com.example.vornx.partner.entity.Partner;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.enums.Sector;
import com.example.vornx.platform.entity.AdminUser;

import java.time.Instant;

public record PartnerResponse(
        Long id,
        String name,
        Sector sector,
        String crNumber,
        String phone,
        String city,
        String country,
        PartnerStatus status,
        String notes,
        Long accountManagerId,
        String accountManagerName,
        Instant createdAt
) {
    public static PartnerResponse from(Partner p) {
        AdminUser manager = p.getAccountManager();
        return new PartnerResponse(p.getId(), p.getName(), p.getSector(), p.getCrNumber(), p.getPhone(),
                p.getCity(), p.getCountry(), p.getStatus(), p.getNotes(),
                manager != null ? manager.getId() : null,
                manager != null ? manager.getFullName() : null,
                p.getCreatedAt());
    }
}
