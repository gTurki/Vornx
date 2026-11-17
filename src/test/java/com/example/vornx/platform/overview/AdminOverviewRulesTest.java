package com.example.vornx.platform.overview;

import com.example.vornx.platform.overview.AdminOverviewResponse.MonthRevenue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The overview chart and its "▲ 14% عن الشهر الماضي" badge. */
class AdminOverviewRulesTest {

    private static final String[] P = {"2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"};

    private static List<MonthRevenue> months(int... amounts) {
        int skip = P.length - amounts.length;
        return IntStream.range(0, amounts.length)
                .mapToObj(i -> new MonthRevenue(P[skip + i], BigDecimal.valueOf(amounts[i]))).toList();
    }

    @Test
    void theChartEndsLastMonthWhileThisMonthIsNotInvoicedYet() {
        List<MonthRevenue> notYet = AdminOverviewService.lastSixMonths(months(1, 2, 3, 4, 5, 6, 0));
        assertEquals("2026-05", notYet.get(0).period());
        assertEquals("2026-10", notYet.get(5).period());

        List<MonthRevenue> invoiced = AdminOverviewService.lastSixMonths(months(1, 2, 3, 4, 5, 6, 7));
        assertEquals("2026-06", invoiced.get(0).period());
        assertEquals("2026-11", invoiced.get(5).period());
    }

    @Test
    void comparesTheLastMonthOfTheChartWithTheOneBefore() {
        assertEquals(14, AdminOverviewService.changePercent(months(10, 20, 30, 40, 50000, 57000)));
        assertEquals(-20, AdminOverviewService.changePercent(months(0, 0, 0, 0, 1000, 800)));
        assertEquals(0, AdminOverviewService.changePercent(months(5, 5)));
    }

    @Test
    void noBadgeWhenThereIsNothingToCompare() {
        assertNull(AdminOverviewService.changePercent(months(0, 0, 0, 0, 0, 5000))); // no month before
        assertNull(AdminOverviewService.changePercent(months(0, 0, 0, 0, 5000, 0)));
        assertNull(AdminOverviewService.changePercent(months(7)));
    }
}
