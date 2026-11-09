package com.example.vornx.access.enums;

/** Why access was denied. Shown to the customer ("تم الرفض: ...") and to the partner. */
public enum DenyReason {
    CARD_INVALID,             // unknown, lost, blocked or expired card
    ACCOUNT_INACTIVE,         // customer account suspended
    METHOD_NOT_SUPPORTED,     // e.g. QR used on a door that only accepts cards
    ACCESS_POINT_UNAVAILABLE, // door/gate is OUT_OF_SERVICE
    NO_PERMIT,                // no permit for this place at all
    PERMIT_INACTIVE,          // permit suspended or revoked
    PERMIT_NOT_YET_VALID,     // starts later (e.g. hotel check-in tomorrow)
    PERMIT_EXPIRED,
    DAY_NOT_ALLOWED,          // e.g. office permit on a Friday
    OUTSIDE_HOURS             // e.g. club at 1:00 AM
}
