package com.example.vornx.subscription.enums;

/** Stored: PENDING, ACTIVE, CANCELLED, REJECTED. EXPIRED is calculated from endAt. */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    EXPIRED,
    CANCELLED,
    REJECTED
}
