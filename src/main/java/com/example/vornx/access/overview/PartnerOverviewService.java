package com.example.vornx.access.overview;

import com.example.vornx.access.entity.Site;
import com.example.vornx.access.enums.AccessPointType;
import com.example.vornx.access.enums.Decision;
import com.example.vornx.access.enums.PermitStatus;
import com.example.vornx.access.overview.PartnerOverviewResponse.AccountManager;
import com.example.vornx.access.overview.PartnerOverviewResponse.Day;
import com.example.vornx.access.overview.PartnerOverviewResponse.SiteStats;
import com.example.vornx.access.repository.SiteRepository;
import com.example.vornx.billing.entity.Contract;
import com.example.vornx.billing.enums.ContractStatus;
import com.example.vornx.billing.repository.ContractRepository;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.partner.service.PartnerService;
import com.example.vornx.platform.entity.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The partner portal's numbers in one request. Aggregates in the database, scoped to the caller's partner. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerOverviewService {

    static final int DAYS = 7;

    private final PartnerOverviewRepository counts;
    private final SiteRepository sites;
    private final ContractRepository contracts;
    private final PartnerService partners;
    private final Clock clock;
    private final ZoneId businessZone;

    public PartnerOverviewResponse overview(Long partnerId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, businessZone);
        Instant startOfToday = today.atStartOfDay(businessZone).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(businessZone).toInstant();
        Instant weekStart = today.minusDays(DAYS - 1).atStartOfDay(businessZone).toInstant();

        Partner partner = partners.getEntity(partnerId);
        AdminUser manager = partner.getAccountManager();
        String plan = contracts.findFirstByPartner_IdAndStatusOrderByEndDateDesc(partnerId, ContractStatus.ACTIVE)
                .map(Contract::getPlanName).orElse(null);

        int offset = businessZone.getRules().getOffset(now).getTotalSeconds();
        List<Day> week = lastDays(today, counts.entriesPerDay(partnerId, weekStart, offset));

        // per site
        Map<Long, long[]> doors = new HashMap<>();
        counts.accessPointsPerSite(partnerId, AccessPointType.ROOM)
                .forEach(r -> doors.put((Long) r[0], new long[]{num(r[1]), num(r[2])}));
        Map<Long, Long> keys = toMap(counts.activeKeysPerSite(partnerId, PermitStatus.ACTIVE, now));
        Map<Long, Long> occupied = toMap(counts.occupiedRoomsPerSite(partnerId, PermitStatus.ACTIVE, now, AccessPointType.ROOM));
        Map<Long, long[]> entries = new HashMap<>(); // [all, allowed]
        for (Object[] r : counts.entriesPerSite(partnerId, startOfToday)) {
            long[] e = entries.computeIfAbsent((Long) r[0], k -> new long[2]);
            e[0] += num(r[2]);
            if (PartnerOverviewRepository.decision(r[1]) == Decision.ALLOW) {
                e[1] += num(r[2]);
            }
        }
        List<SiteStats> siteStats = new ArrayList<>();
        for (Site s : sites.findByPartner_IdOrderByName(partnerId)) {
            long[] d = doors.getOrDefault(s.getId(), new long[2]);
            long[] e = entries.getOrDefault(s.getId(), new long[2]);
            siteStats.add(new SiteStats(s.getId(), s.getName(), s.getType(), s.getCity(), d[0], d[1],
                    occupied.getOrDefault(s.getId(), 0L), keys.getOrDefault(s.getId(), 0L), e[0], e[1]));
        }

        return new PartnerOverviewResponse(
                plan,
                manager == null ? null : new AccountManager(manager.getFullName(), manager.getEmail(), manager.getPhone()),
                week,
                counts.startingBetween(partnerId, PermitStatus.REVOKED, startOfToday, startOfTomorrow),
                counts.endingBetween(partnerId, PermitStatus.REVOKED, startOfToday, startOfTomorrow),
                keys.values().stream().mapToLong(Long::longValue).sum(),
                siteStats);
    }

    /** The last 7 local days, oldest first, 0 for a day without entries. Rows: [date, decision, count]. */
    static List<Day> lastDays(LocalDate today, List<Object[]> rows) {
        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (Object[] r : rows) {
            LocalDate day = r[0] instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(r[0].toString());
            long[] c = byDay.computeIfAbsent(day, k -> new long[2]);
            c[PartnerOverviewRepository.decision(r[1]) == Decision.ALLOW ? 0 : 1] += num(r[2]);
        }
        List<Day> days = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long[] c = byDay.getOrDefault(day, new long[2]);
            days.add(new Day(day, c[0], c[1]));
        }
        return days;
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        rows.forEach(r -> m.put((Long) r[0], num(r[1])));
        return m;
    }

    /** COUNT/SUM come back as Long, BigInteger or BigDecimal depending on the query type. */
    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
