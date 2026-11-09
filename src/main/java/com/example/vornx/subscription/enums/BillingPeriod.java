package com.example.vornx.subscription.enums;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** How long one payment lasts. */
public enum BillingPeriod {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY;

    /**
     * Uses calendar math in the business time zone: 31 Jan + 1 month = 28/29 Feb (not "+30 days").
     */
    public Instant addTo(Instant start, ZoneId zone) {
        ZonedDateTime z = start.atZone(zone);
        ZonedDateTime end = switch (this) {
            case WEEKLY -> z.plusWeeks(1);
            case MONTHLY -> z.plusMonths(1);
            case QUARTERLY -> z.plusMonths(3);
            case YEARLY -> z.plusYears(1);
        };
        return end.toInstant();
    }
}
