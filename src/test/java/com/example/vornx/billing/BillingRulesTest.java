package com.example.vornx.billing;

import com.example.vornx.billing.entity.Contract;
import com.example.vornx.billing.entity.Invoice;
import com.example.vornx.billing.enums.ContractStatus;
import com.example.vornx.billing.enums.InvoiceStatus;
import com.example.vornx.billing.enums.PlanTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private static Contract contractEnding(LocalDate end) {
        return new Contract("CT-1", null, "باقة الضيافة", PlanTier.ENTERPRISE, new BigDecimal("12000.00"),
                LocalDate.of(2026, 3, 1), end);
    }

    @Test
    void contractStatusFollowsTheEndDate() {
        assertEquals(ContractStatus.ACTIVE, contractEnding(LocalDate.of(2027, 2, 28)).effectiveStatus(TODAY));       // 111 days left
        assertEquals(ContractStatus.EXPIRING_SOON, contractEnding(LocalDate.of(2026, 12, 31)).effectiveStatus(TODAY)); // 52 days left
        assertEquals(ContractStatus.EXPIRING_SOON, contractEnding(TODAY).effectiveStatus(TODAY));                     // last day
        assertEquals(ContractStatus.EXPIRED, contractEnding(TODAY.minusDays(1)).effectiveStatus(TODAY));

        Contract terminated = contractEnding(LocalDate.of(2027, 2, 28));
        terminated.setStatus(ContractStatus.TERMINATED);
        assertEquals(ContractStatus.TERMINATED, terminated.effectiveStatus(TODAY));
    }

    @Test
    void renewingExtendsFromTheEndDate_orFromTodayIfAlreadyExpired() {
        Contract running = contractEnding(LocalDate.of(2027, 2, 28));
        running.renew(12, TODAY);
        assertEquals(LocalDate.of(2028, 2, 29), running.getEndDate()); // 2028 is a leap year
        assertEquals(1, running.getRenewalCount());

        Contract expired = contractEnding(LocalDate.of(2026, 10, 1));
        expired.renew(12, TODAY);
        assertEquals(LocalDate.of(2027, 11, 8), expired.getEndDate()); // 12 months from today, no "free" gap months
    }

    @Test
    void invoiceBecomesOverdueTheDayAfterItsDueDate_unlessPaid() {
        Invoice invoice = new Invoice("VX-2026-000001", contractEnding(LocalDate.of(2027, 2, 28)),
                YearMonth.of(2026, 11), TODAY, TODAY.plusDays(15));
        assertEquals(InvoiceStatus.DUE, invoice.effectiveStatus(TODAY.plusDays(15)));
        assertEquals(InvoiceStatus.OVERDUE, invoice.effectiveStatus(TODAY.plusDays(16)));
        assertEquals(new BigDecimal("12000.00"), invoice.getAmount()); // copied from the contract

        invoice.markPaid(Instant.parse("2026-11-30T10:00:00Z"));
        assertEquals(InvoiceStatus.PAID, invoice.effectiveStatus(TODAY.plusDays(100)));
    }
}
