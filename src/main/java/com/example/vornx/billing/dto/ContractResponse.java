package com.example.vornx.billing.dto;

import com.example.vornx.billing.entity.Contract;
import com.example.vornx.billing.enums.ContractStatus;
import com.example.vornx.billing.enums.PlanTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ContractResponse(
        Long id,
        String number,
        Long partnerId,
        String partnerName,
        String planName,
        PlanTier tier,
        BigDecimal monthlyFee,
        Integer maxBeneficiaries,
        LocalDate startDate,
        LocalDate endDate,
        long daysLeft,          // negative = days since it expired
        int progressPercent,    // "مضى 65% من مدة العقد"
        int renewalCount,
        ContractStatus status,
        String notes
) {
    public static ContractResponse from(Contract c, LocalDate today) {
        long total = Math.max(1, ChronoUnit.DAYS.between(c.getStartDate(), c.getEndDate()));
        long elapsed = ChronoUnit.DAYS.between(c.getStartDate(), today);
        int progress = (int) Math.max(0, Math.min(100, elapsed * 100 / total));
        return new ContractResponse(c.getId(), c.getNumber(), c.getPartner().getId(), c.getPartner().getName(),
                c.getPlanName(), c.getTier(), c.getMonthlyFee(), c.getMaxBeneficiaries(), c.getStartDate(),
                c.getEndDate(), ChronoUnit.DAYS.between(today, c.getEndDate()), progress, c.getRenewalCount(),
                c.effectiveStatus(today), c.getNotes());
    }
}
