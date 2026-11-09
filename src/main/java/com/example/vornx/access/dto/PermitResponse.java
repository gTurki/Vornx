package com.example.vornx.access.dto;

import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.entity.Permit;
import com.example.vornx.access.enums.AccessLevel;
import com.example.vornx.access.enums.PermitStatus;
import com.example.vornx.access.enums.PermitType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public record PermitResponse(
        Long id,
        String permitNumber,
        String verificationCode,
        PermitType type,
        AccessLevel level,
        PermitStatus status,
        String holderVornxId,
        String holderName,
        Long partnerId,
        String partnerName,
        Long siteId,
        String siteName,
        boolean wholeSite,
        List<AccessPointRef> accessPoints,
        Instant validFrom,
        Instant validUntil,
        TreeSet<DayOfWeek> allowedDays,
        LocalTime allowedFrom,
        LocalTime allowedTo,
        String conditions
) {
    public record AccessPointRef(Long id, String code, String name) {
    }

    public static PermitResponse from(Permit p, Instant now) {
        List<AccessPointRef> aps = p.getAccessPoints().stream()
                .sorted(Comparator.comparing(AccessPoint::getCode))
                .map(ap -> new AccessPointRef(ap.getId(), ap.getCode(), ap.getName()))
                .toList();
        return new PermitResponse(p.getId(), p.getPermitNumber(), p.getVerificationCode(), p.getType(), p.getLevel(),
                p.effectiveStatus(now),
                p.getCustomer().getVornxId(), p.getCustomer().getFirstName() + " " + p.getCustomer().getLastName(),
                p.getPartner().getId(), p.getPartner().getName(),
                p.getSite().getId(), p.getSite().getName(),
                p.coversWholeSite(), aps,
                p.getValidFrom(), p.getValidUntil(),
                new TreeSet<>(p.getAllowedDays()), p.getAllowedFrom(), p.getAllowedTo(), p.getConditions());
    }
}
