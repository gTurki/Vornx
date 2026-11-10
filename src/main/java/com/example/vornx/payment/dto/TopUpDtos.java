package com.example.vornx.payment.dto;

import com.example.vornx.payment.entity.TopUp;
import com.example.vornx.payment.enums.TopUpStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class TopUpDtos {

    private TopUpDtos() {
    }

    public record StartTopUpRequest(
            @NotNull @DecimalMin("10.00") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2) BigDecimal amount
    ) {
    }

    /** checkoutUrl is only sent while the top-up can still be paid. */
    public record TopUpResponse(
            String reference,
            BigDecimal amount,
            String currency,
            TopUpStatus status,
            String checkoutUrl,
            Instant createdAt,
            Instant paidAt
    ) {
        public static TopUpResponse from(TopUp t) {
            return new TopUpResponse(t.getReference(), t.getAmount(), t.getCurrency(), t.getStatus(),
                    t.isPending() ? t.getCheckoutUrl() : null, t.getCreatedAt(), t.getPaidAt());
        }
    }

    /** What the wallet screen may offer. */
    public record TopUpOptions(
            boolean cardEnabled,      // Moyasar is configured
            boolean testMode,         // Moyasar test keys: use test cards, no real money
            boolean simulatedEnabled, // dev only: instant fake top-up
            BigDecimal min,
            BigDecimal max
    ) {
    }
}
