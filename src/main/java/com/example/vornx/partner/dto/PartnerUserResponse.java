package com.example.vornx.partner.dto;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.partner.entity.PartnerUser;
import com.example.vornx.partner.enums.PartnerRole;

import java.time.Instant;

public record PartnerUserResponse(
        Long id,
        String fullName,
        String email,
        PartnerRole role,
        AccountStatus status,
        Long partnerId,
        String partnerName,
        Instant lastLoginAt
) {
    public static PartnerUserResponse from(PartnerUser u) {
        return new PartnerUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getStatus(),
                u.getPartner().getId(), u.getPartner().getName(), u.getLastLoginAt());
    }
}
