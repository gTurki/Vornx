package com.example.vornx.billing.dto;

import com.example.vornx.billing.enums.PlanTier;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** e.g. {"planName":"باقة الضيافة","tier":"ENTERPRISE","monthlyFee":12000,"maxBeneficiaries":40000,
 *        "startDate":"2026-03-01","endDate":"2027-02-28"} */
public record ContractRequest(
        @NotBlank @Size(max = 100) String planName,
        @NotNull PlanTier tier,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal monthlyFee,
        @Min(1) Integer maxBeneficiaries,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 1000) String notes
) {
}
