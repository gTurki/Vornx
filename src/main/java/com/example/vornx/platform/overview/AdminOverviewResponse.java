package com.example.vornx.platform.overview;

import com.example.vornx.billing.enums.ContractStatus;
import com.example.vornx.billing.enums.PlanTier;
import com.example.vornx.partner.enums.Sector;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything on the admin dashboard home ("نظرة عامة" in index 55), from real data.
 *
 * @param sectors           the strip: partners per sector (all 8 sectors, 0 when none)
 * @param customers         "مستفيد نهائي": active customer accounts
 * @param mrrChangePercent  invoiced last month vs the month before, null when it can't be compared
 * @param revenueByMonth    the chart: invoiced per month, the last 6 months, oldest first
 * @param attention         "تحتاج متابعة الآن": contracts ending soon / ended, then overdue invoices
 * @param topPartners       "أكبر الجهات إيرادًا": the 5 largest running contracts
 */
public record AdminOverviewResponse(
        List<SectorCount> sectors,
        long customers,
        BigDecimal monthlyRecurringRevenue,
        Integer mrrChangePercent,
        long activePartners,
        long contractsNeedingAttention,
        long overdueInvoices,
        BigDecimal overdueAmount,
        long openSupportTickets,
        List<MonthRevenue> revenueByMonth,
        List<Attention> attention,
        List<TopPartner> topPartners
) {
    public record SectorCount(Sector sector, long partners) {
    }

    /** period: "2026-09" */
    public record MonthRevenue(String period, BigDecimal amount) {
    }

    /**
     * kind CONTRACT: daysLeft (negative = ended), contractStatus.
     * kind INVOICE:  an overdue invoice, daysLeft = -days late, amount.
     */
    public record Attention(String kind, Long id, Long partnerId, String partnerName, String detail,
                            long daysLeft, ContractStatus contractStatus, BigDecimal amount) {
    }

    public record TopPartner(Long partnerId, String name, Sector sector, String city, String planName, PlanTier tier,
                             long beneficiaries, BigDecimal monthlyFee, ContractStatus contractStatus) {
    }
}
