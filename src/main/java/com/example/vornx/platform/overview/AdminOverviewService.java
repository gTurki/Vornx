package com.example.vornx.platform.overview;

import com.example.vornx.access.enums.PermitStatus;
import com.example.vornx.access.repository.PermitRepository;
import com.example.vornx.billing.entity.Contract;
import com.example.vornx.billing.entity.Invoice;
import com.example.vornx.billing.enums.ContractStatus;
import com.example.vornx.billing.enums.InvoiceStatus;
import com.example.vornx.billing.repository.ContractRepository;
import com.example.vornx.billing.repository.InvoiceRepository;
import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.customer.repository.CustomerRepository;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.enums.Sector;
import com.example.vornx.partner.repository.PartnerRepository;
import com.example.vornx.platform.overview.AdminOverviewResponse.Attention;
import com.example.vornx.platform.overview.AdminOverviewResponse.MonthRevenue;
import com.example.vornx.platform.overview.AdminOverviewResponse.SectorCount;
import com.example.vornx.platform.overview.AdminOverviewResponse.TopPartner;
import com.example.vornx.support.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin home page in ONE request: a handful of aggregate queries (counts and sums in the database),
 * never "load every row and count in Java".
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOverviewService {

    static final int MONTHS = 6;
    static final int TOP = 5;

    private final PartnerRepository partners;
    private final CustomerRepository customers;
    private final ContractRepository contracts;
    private final InvoiceRepository invoices;
    private final PermitRepository permits;
    private final SupportService support;
    private final Clock clock;
    private final ZoneId businessZone;

    public AdminOverviewResponse overview() {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));

        // strip: every sector, 0 when it has no partner yet
        Map<Sector, Long> bySector = new EnumMap<>(Sector.class);
        Arrays.stream(Sector.values()).forEach(s -> bySector.put(s, 0L));
        for (Object[] row : partners.countBySector(PartnerStatus.SUSPENDED)) {
            bySector.put((Sector) row[0], (Long) row[1]);
        }

        // chart: 6 months, oldest first (7 are read: the current month may have no invoices yet)
        YearMonth now = YearMonth.from(today);
        Map<String, BigDecimal> months = new LinkedHashMap<>();
        for (int i = MONTHS; i >= 0; i--) {
            months.put(now.minusMonths(i).toString(), BigDecimal.ZERO);
        }
        for (Object[] row : invoices.sumByPeriod(months.keySet())) {
            months.put((String) row[0], (BigDecimal) row[1]);
        }
        List<MonthRevenue> revenue = lastSixMonths(
                months.entrySet().stream().map(e -> new MonthRevenue(e.getKey(), e.getValue())).toList());

        List<Contract> running = contracts.findByStatusOrderByEndDateAsc(ContractStatus.ACTIVE);

        return new AdminOverviewResponse(
                bySector.entrySet().stream().map(e -> new SectorCount(e.getKey(), e.getValue())).toList(),
                customers.countByStatus(AccountStatus.ACTIVE),
                contracts.sumMonthlyFees(ContractStatus.ACTIVE, today),
                changePercent(revenue),
                partners.countByStatus(PartnerStatus.ACTIVE),
                contracts.countNeedingAttention(ContractStatus.ACTIVE, today.plusDays(Contract.EXPIRING_SOON_DAYS)),
                invoices.countOverdue(InvoiceStatus.DUE, today),
                invoices.sumOverdue(InvoiceStatus.DUE, today),
                support.countUnresolved(),
                revenue,
                attention(running, today),
                topPartners(running, today));
    }

    /**
     * From 7 months (oldest first, the last = this month): the 6 to show. While this month's invoices aren't issued
     * yet, the chart ends at last month, so it doesn't look like revenue dropped to zero.
     */
    static List<MonthRevenue> lastSixMonths(List<MonthRevenue> seven) {
        boolean thisMonthInvoiced = seven.get(seven.size() - 1).amount().signum() > 0;
        return thisMonthInvoiced ? seven.subList(1, seven.size()) : seven.subList(0, seven.size() - 1);
    }

    /** "▲ 14% عن الشهر الماضي": the chart's last month vs the one before. Null when there is nothing to compare. */
    static Integer changePercent(List<MonthRevenue> revenue) {
        if (revenue.size() < 2) {
            return null;
        }
        BigDecimal last = revenue.get(revenue.size() - 1).amount();
        BigDecimal before = revenue.get(revenue.size() - 2).amount();
        if (before.signum() <= 0 || last.signum() <= 0) {
            return null;
        }
        return last.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 0, RoundingMode.HALF_UP).intValue();
    }

    /** Contracts ending soon or ended (soonest first), then overdue invoices (oldest first). */
    private List<Attention> attention(List<Contract> running, LocalDate today) {
        List<Attention> list = new ArrayList<>();
        running.stream()
                .filter(c -> c.effectiveStatus(today) != ContractStatus.ACTIVE)
                .forEach(c -> list.add(new Attention("CONTRACT", c.getId(), c.getPartner().getId(), c.getPartner().getName(),
                        c.getPlanName(), ChronoUnit.DAYS.between(today, c.getEndDate()), c.effectiveStatus(today), null)));
        invoices.findByStatusAndDueDateBefore(InvoiceStatus.DUE, today,
                        PageRequest.of(0, 10, Sort.by("dueDate"))).getContent()
                .forEach((Invoice i) -> list.add(new Attention("INVOICE", i.getId(), i.getPartner().getId(),
                        i.getPartner().getName(), i.getNumber(), ChronoUnit.DAYS.between(today, i.getDueDate()), null,
                        i.getAmount())));
        return list;
    }

    /** The biggest running contracts, one per partner, with how many customers hold a valid permit from them. */
    private List<TopPartner> topPartners(List<Contract> running, LocalDate today) {
        Map<Long, Contract> biggestPerPartner = new HashMap<>();
        running.forEach(c -> biggestPerPartner.merge(c.getPartner().getId(), c,
                (a, b) -> a.getMonthlyFee().compareTo(b.getMonthlyFee()) >= 0 ? a : b));
        List<Contract> top = biggestPerPartner.values().stream()
                .sorted(Comparator.comparing(Contract::getMonthlyFee).reversed())
                .limit(TOP)
                .toList();
        if (top.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> beneficiaries = new HashMap<>();
        for (Object[] row : permits.countCustomersByPartner(top.stream().map(c -> c.getPartner().getId()).toList(),
                PermitStatus.ACTIVE, clock.instant())) {
            beneficiaries.put((Long) row[0], (Long) row[1]);
        }
        return top.stream().map(c -> new TopPartner(c.getPartner().getId(), c.getPartner().getName(),
                c.getPartner().getSector(), c.getPartner().getCity(), c.getPlanName(), c.getTier(),
                beneficiaries.getOrDefault(c.getPartner().getId(), 0L), c.getMonthlyFee(),
                c.effectiveStatus(today))).toList();
    }
}
