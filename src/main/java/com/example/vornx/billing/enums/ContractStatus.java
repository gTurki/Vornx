package com.example.vornx.billing.enums;

/** Stored: ACTIVE, TERMINATED. EXPIRING_SOON ("ينتهي قريبًا") and EXPIRED ("متأخر") are calculated from endDate. */
public enum ContractStatus {
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
    TERMINATED
}
