package com.example.vornx.billing.dto;

import java.math.BigDecimal;

/** The numbers on the admin dashboard home (index 55). */
public record BillingSummaryResponse(
        long activePartners,          // "جهة شريكة"
        long runningContracts,
        BigDecimal monthlyRecurringRevenue, // "الإيراد الشهري، ر.س"
        long contractsNeedingAttention,     // "عقود تحتاج متابعة"
        long overdueInvoices,
        BigDecimal overdueAmount,
        BigDecimal unpaidAmount,
        long openSupportTickets
) {
}
