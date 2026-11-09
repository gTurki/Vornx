package com.example.vornx.subscription.dto;

import com.example.vornx.subscription.entity.Subscription;
import com.example.vornx.subscription.enums.BillingPeriod;
import com.example.vornx.subscription.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** "اشتراكاتي": status, dates, renewal price and who renews it. */
public record SubscriptionResponse(
        Long id,
        String number,
        Long planId,
        String planName,
        String partnerName,
        String siteName,
        BillingPeriod billingPeriod,
        BigDecimal renewalPrice,
        SubscriptionStatus status,
        Instant startAt,
        Instant endAt,
        String permitNumber,
        String customerVornxId,
        String customerName
) {
    public static SubscriptionResponse from(Subscription s, Instant now) {
        return new SubscriptionResponse(s.getId(), s.getNumber(), s.getPlan().getId(), s.getPlan().getName(),
                s.getPartner().getName(), s.getPlan().getSite().getName(), s.getPlan().getBillingPeriod(),
                s.getPlan().getPrice(), s.effectiveStatus(now), s.getStartAt(), s.getEndAt(),
                s.getPermit() != null ? s.getPermit().getPermitNumber() : null,
                s.getCustomer().getVornxId(),
                s.getCustomer().getFirstName() + " " + s.getCustomer().getLastName());
    }
}
