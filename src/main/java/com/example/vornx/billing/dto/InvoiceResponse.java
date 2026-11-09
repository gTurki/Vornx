package com.example.vornx.billing.dto;

import com.example.vornx.billing.entity.Invoice;
import com.example.vornx.billing.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvoiceResponse(
        Long id,
        String number,
        Long partnerId,
        String partnerName,
        String contractNumber,
        String period,
        BigDecimal amount,
        String currency,
        LocalDate issueDate,
        LocalDate dueDate,
        Instant paidAt,
        InvoiceStatus status
) {
    public static InvoiceResponse from(Invoice i, LocalDate today) {
        return new InvoiceResponse(i.getId(), i.getNumber(), i.getPartner().getId(), i.getPartner().getName(),
                i.getContract().getNumber(), i.getPeriod(), i.getAmount(), "SAR", i.getIssueDate(), i.getDueDate(),
                i.getPaidAt(), i.effectiveStatus(today));
    }
}
