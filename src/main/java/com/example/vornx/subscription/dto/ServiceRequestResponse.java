package com.example.vornx.subscription.dto;

import com.example.vornx.subscription.entity.ServiceRequest;
import com.example.vornx.subscription.enums.RequestStatus;
import com.example.vornx.subscription.enums.RequestType;

import java.math.BigDecimal;
import java.time.Instant;

public record ServiceRequestResponse(
        Long id,
        String requestNumber,
        RequestType type,
        RequestStatus status,
        BigDecimal amount,
        Long subscriptionId,
        String subscriptionNumber,
        String planName,
        String customerVornxId,
        String customerName,
        String rejectionReason,
        Instant createdAt,
        Instant decidedAt
) {
    public static ServiceRequestResponse from(ServiceRequest r) {
        return new ServiceRequestResponse(r.getId(), r.getRequestNumber(), r.getType(), r.getStatus(), r.getAmount(),
                r.getSubscription().getId(), r.getSubscription().getNumber(), r.getSubscription().getPlan().getName(),
                r.getCustomer().getVornxId(), r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName(),
                r.getRejectionReason(), r.getCreatedAt(), r.getDecidedAt());
    }
}
