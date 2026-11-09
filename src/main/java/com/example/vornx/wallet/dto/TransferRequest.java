package com.example.vornx.wallet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** "تحويل" - send money to another VORNX customer, e.g. {"toVornxId":"VX...","amount":150.00,"note":"Pocket money"} */
public record TransferRequest(
        @NotBlank String toVornxId,
        @NotNull @DecimalMin("1.00") @DecimalMax("5000.00") @Digits(integer = 4, fraction = 2) BigDecimal amount,
        @Size(max = 100) String note
) {
}
