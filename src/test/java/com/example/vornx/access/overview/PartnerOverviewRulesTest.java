package com.example.vornx.access.overview;

import com.example.vornx.access.overview.PartnerOverviewResponse.Day;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The home chart: always 7 days, oldest first, 0 for quiet days, allowed and denied counted apart. */
class PartnerOverviewRulesTest {

    @Test
    void sevenDaysOldestFirstWithEmptyDays() {
        LocalDate today = LocalDate.parse("2026-11-18");
        List<Object[]> rows = List.of(
                new Object[]{java.sql.Date.valueOf("2026-11-18"), "ALLOW", BigInteger.valueOf(12)},
                new Object[]{java.sql.Date.valueOf("2026-11-18"), "DENY", 3L},
                new Object[]{"2026-11-13", "ALLOW", 5L},
                new Object[]{"2026-11-01", "ALLOW", 99L}); // outside the week: ignored

        List<Day> days = PartnerOverviewService.lastDays(today, rows);

        assertEquals(7, days.size());
        assertEquals(LocalDate.parse("2026-11-12"), days.get(0).date());
        assertEquals(new Day(LocalDate.parse("2026-11-13"), 5, 0), days.get(1));
        assertEquals(new Day(LocalDate.parse("2026-11-15"), 0, 0), days.get(3));
        assertEquals(new Day(today, 12, 3), days.get(6));
    }
}
