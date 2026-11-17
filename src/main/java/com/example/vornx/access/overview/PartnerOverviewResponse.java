package com.example.vornx.access.overview;

import com.example.vornx.access.enums.SiteType;

import java.time.LocalDate;
import java.util.List;

/**
 * The numbers of the partner portal (hotel.html): sidebar, home chart, guests & keys, sites table. All real data.
 *
 * @param planName         the running contract's plan ("باقة الضيافة"), null without a contract
 * @param accountManager   the VORNX employee looking after this partner ("مدير حسابك"), null if none yet
 * @param lastSevenDays    entries per day (Riyadh dates), oldest first: allowed and denied
 * @param arrivalsToday    keys that start today; departuresToday: keys that end today
 * @param activeKeys       permits valid right now
 * @param sites            per site: doors, rooms, rooms with a valid key, valid keys, entries today
 */
public record PartnerOverviewResponse(
        String planName,
        AccountManager accountManager,
        List<Day> lastSevenDays,
        long arrivalsToday,
        long departuresToday,
        long activeKeys,
        List<SiteStats> sites
) {
    public record AccountManager(String name, String email, String phone) {
    }

    public record Day(LocalDate date, long allowed, long denied) {
    }

    public record SiteStats(Long siteId, String name, SiteType type, String city, long accessPoints, long rooms,
                            long occupiedRooms, long activeKeys, long entriesToday, long allowedToday) {
    }
}
