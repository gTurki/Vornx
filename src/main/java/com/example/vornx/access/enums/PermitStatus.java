package com.example.vornx.access.enums;

/** Only ACTIVE, SUSPENDED and REVOKED are stored. PENDING (not started yet) and EXPIRED are calculated from the dates. */
public enum PermitStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    REVOKED
}
