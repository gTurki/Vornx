package com.example.vornx.access.service;

import com.example.vornx.access.entity.Permit;
import com.example.vornx.access.enums.DenyReason;
import com.example.vornx.access.enums.PermitStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The time rules of a permit, as PURE logic: no database, no Spring.
 * That makes it easy to unit-test every case (see PermitRulesTest).
 */
public final class PermitRules {

    private PermitRules() {
    }

    /**
     * @param reason null when the permit allows access right now
     * @param rank   how far the check got (higher = closer to allowed). When a customer has several
     *               permits and all fail, we report the reason of the one that got furthest.
     */
    public record Result(DenyReason reason, int rank) {
        public boolean allowed() {
            return reason == null;
        }
    }

    public static Result check(Permit permit, Instant now, ZoneId zone) {
        PermitStatus status = permit.effectiveStatus(now);
        switch (status) {
            case REVOKED, SUSPENDED -> {
                return new Result(DenyReason.PERMIT_INACTIVE, 1);
            }
            case PENDING -> {
                return new Result(DenyReason.PERMIT_NOT_YET_VALID, 1);
            }
            case EXPIRED -> {
                return new Result(DenyReason.PERMIT_EXPIRED, 1);
            }
            default -> {
                // ACTIVE: continue with day and time checks
            }
        }

        ZonedDateTime local = now.atZone(zone);
        if (!permit.getAllowedDays().isEmpty() && !permit.getAllowedDays().contains(local.getDayOfWeek())) {
            return new Result(DenyReason.DAY_NOT_ALLOWED, 2);
        }
        if (permit.getAllowedFrom() != null
                && !isWithinWindow(local.toLocalTime(), permit.getAllowedFrom(), permit.getAllowedTo())) {
            return new Result(DenyReason.OUTSIDE_HOURS, 3);
        }
        return new Result(null, 4);
    }

    /**
     * Supports normal windows (08:00-17:00) and overnight ones (22:00-06:00).
     * from == to means the whole day.
     */
    public static boolean isWithinWindow(LocalTime t, LocalTime from, LocalTime to) {
        if (from.equals(to)) {
            return true;
        }
        if (from.isBefore(to)) {
            return !t.isBefore(from) && t.isBefore(to);
        }
        return !t.isBefore(from) || t.isBefore(to); // overnight
    }
}
