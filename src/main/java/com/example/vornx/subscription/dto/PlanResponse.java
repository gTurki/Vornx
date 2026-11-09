package com.example.vornx.subscription.dto;

import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.enums.PermitType;
import com.example.vornx.subscription.entity.SubscriptionPlan;
import com.example.vornx.subscription.enums.BillingPeriod;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public record PlanResponse(
        Long id,
        Long partnerId,
        String partnerName,
        Long siteId,
        String siteName,
        String name,
        String description,
        PermitType permitType,
        BillingPeriod billingPeriod,
        BigDecimal price,
        String currency,
        boolean requiresApproval,
        boolean wholeSite,
        List<String> accessPointNames,
        TreeSet<DayOfWeek> allowedDays,
        LocalTime allowedFrom,
        LocalTime allowedTo,
        boolean active
) {
    public static PlanResponse from(SubscriptionPlan p) {
        List<String> aps = p.getAccessPoints().stream()
                .sorted(Comparator.comparing(AccessPoint::getCode))
                .map(AccessPoint::getName)
                .toList();
        return new PlanResponse(p.getId(), p.getPartner().getId(), p.getPartner().getName(),
                p.getSite().getId(), p.getSite().getName(), p.getName(), p.getDescription(),
                p.getPermitType(), p.getBillingPeriod(), p.getPrice(), "SAR", p.isRequiresApproval(),
                aps.isEmpty(), aps, new TreeSet<>(p.getAllowedDays()), p.getAllowedFrom(), p.getAllowedTo(),
                p.isActive());
    }
}
