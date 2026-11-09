package com.example.vornx.access.service;

import com.example.vornx.access.entity.Permit;
import com.example.vornx.access.enums.AccessLevel;
import com.example.vornx.access.enums.DenyReason;
import com.example.vornx.access.enums.PermitStatus;
import com.example.vornx.access.enums.PermitType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests: no Spring, no database, they run in milliseconds.
 * "now" is fixed so the results never depend on when the test runs.
 */
class PermitRulesTest {

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    /** Tuesday 10 Nov 2026, 10:00 in Riyadh (07:00 UTC). */
    private static final Instant NOW = Instant.parse("2026-11-10T07:00:00Z");

    private static Permit permit(Instant from, Instant until) {
        return Permit.issue("PRM-TEST", "00000000", null, null, null,
                PermitType.WORKPLACE, AccessLevel.STANDARD, from, until);
    }

    private static Permit validNow() {
        return permit(NOW.minus(1, ChronoUnit.DAYS), NOW.plus(30, ChronoUnit.DAYS));
    }

    @Test
    void allowsAValidPermitWithNoRestrictions() {
        assertTrue(PermitRules.check(validNow(), NOW, RIYADH).allowed());
    }

    @Test
    void deniesRevokedPermit() {
        Permit p = validNow();
        p.setStatus(PermitStatus.REVOKED);
        assertEquals(DenyReason.PERMIT_INACTIVE, PermitRules.check(p, NOW, RIYADH).reason());
    }

    @Test
    void deniesPermitThatHasNotStarted() {
        Permit p = permit(NOW.plus(1, ChronoUnit.HOURS), NOW.plus(2, ChronoUnit.DAYS)); // check-in later today
        assertEquals(DenyReason.PERMIT_NOT_YET_VALID, PermitRules.check(p, NOW, RIYADH).reason());
    }

    @Test
    void deniesExpiredPermit_andTheEndIsExclusive() {
        Permit p = permit(NOW.minus(3, ChronoUnit.DAYS), NOW); // ended exactly now
        assertEquals(DenyReason.PERMIT_EXPIRED, PermitRules.check(p, NOW, RIYADH).reason());
    }

    @Test
    void deniesWrongDay_usingRiyadhTimeNotUtc() {
        Permit p = validNow();
        p.getAllowedDays().add(DayOfWeek.SUNDAY);
        assertEquals(DenyReason.DAY_NOT_ALLOWED, PermitRules.check(p, NOW, RIYADH).reason());

        // 2026-11-10T22:30Z is still Tuesday in UTC, but already Wednesday 01:30 in Riyadh.
        Permit wednesdayOnly = validNow();
        wednesdayOnly.getAllowedDays().add(DayOfWeek.WEDNESDAY);
        assertTrue(PermitRules.check(wednesdayOnly, Instant.parse("2026-11-10T22:30:00Z"), RIYADH).allowed());
    }

    @Test
    void deniesOutsideOfficeHours() {
        Permit p = validNow();
        p.setAllowedFrom(LocalTime.of(8, 0));
        p.setAllowedTo(LocalTime.of(17, 0));
        assertTrue(PermitRules.check(p, NOW, RIYADH).allowed()); // 10:00 Riyadh

        Instant eveningRiyadh = Instant.parse("2026-11-10T15:30:00Z"); // 18:30 Riyadh
        assertEquals(DenyReason.OUTSIDE_HOURS, PermitRules.check(p, eveningRiyadh, RIYADH).reason());
    }

    @Test
    void timeWindowRules() {
        LocalTime eight = LocalTime.of(8, 0);
        LocalTime five = LocalTime.of(17, 0);
        assertTrue(PermitRules.isWithinWindow(eight, eight, five));               // start is included
        assertFalse(PermitRules.isWithinWindow(five, eight, five));               // end is excluded
        // Overnight shift 22:00 -> 06:00
        LocalTime ten = LocalTime.of(22, 0);
        LocalTime six = LocalTime.of(6, 0);
        assertTrue(PermitRules.isWithinWindow(LocalTime.of(23, 30), ten, six));
        assertTrue(PermitRules.isWithinWindow(LocalTime.of(3, 0), ten, six));
        assertFalse(PermitRules.isWithinWindow(LocalTime.of(12, 0), ten, six));
    }

    @Test
    void rankTellsWhichFailureGotFurthest() {
        Permit expired = permit(NOW.minus(5, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        Permit wrongHours = validNow();
        wrongHours.setAllowedFrom(LocalTime.of(18, 0));
        wrongHours.setAllowedTo(LocalTime.of(23, 0));

        PermitRules.Result a = PermitRules.check(expired, NOW, RIYADH);
        PermitRules.Result b = PermitRules.check(wrongHours, NOW, RIYADH);
        assertTrue(b.rank() > a.rank()); // "outside hours" is more useful to show than "expired"
        assertNull(PermitRules.check(validNow(), NOW, RIYADH).reason());
    }
}
