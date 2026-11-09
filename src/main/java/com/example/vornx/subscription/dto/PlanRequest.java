package com.example.vornx.subscription.dto;

import com.example.vornx.access.enums.PermitType;
import com.example.vornx.subscription.enums.BillingPeriod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * Example - gym:
 * {"siteId":1,"name":"Fitness Plus - Annual","permitType":"CLUB","billingPeriod":"YEARLY","price":2400.00,
 *  "accessPointIds":[3],"allowedFrom":"05:00","allowedTo":"23:59"}
 */
public record PlanRequest(
        @NotNull Long siteId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @NotNull PermitType permitType,
        @NotNull BillingPeriod billingPeriod,
        @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2) BigDecimal price,
        Boolean requiresApproval, // default true
        Set<Long> accessPointIds, // empty = whole site
        Set<DayOfWeek> allowedDays,
        LocalTime allowedFrom,
        LocalTime allowedTo,
        Boolean active            // default true
) {
}
