package com.example.vornx.access.dto;

import com.example.vornx.access.entity.AccessDecision;
import com.example.vornx.access.enums.AccessMethod;
import com.example.vornx.access.enums.Decision;
import com.example.vornx.access.enums.DenyReason;
import com.example.vornx.access.enums.Direction;
import com.example.vornx.access.enums.EnforcementResult;
import com.example.vornx.customer.entity.Customer;

import java.time.Instant;

/** One line of an access log (customer "entry & exit log", partner "live events"). */
public record AccessEventResponse(
        String decisionNumber,
        Instant occurredAt,
        Long siteId,
        String siteName,
        String accessPointCode,
        String accessPointName,
        AccessMethod method,
        Direction direction,
        Decision decision,
        DenyReason denyReason,
        EnforcementResult enforcementResult,
        String holderVornxId,
        String holderName,
        String permitNumber
) {
    public static AccessEventResponse from(AccessDecision d) {
        Customer c = d.getCustomer();
        return new AccessEventResponse(d.getDecisionNumber(), d.getOccurredAt(),
                d.getAccessPoint().getSite().getId(), d.getAccessPoint().getSite().getName(),
                d.getAccessPoint().getCode(), d.getAccessPoint().getName(),
                d.getMethod(), d.getDirection(), d.getDecision(), d.getDenyReason(), d.getEnforcementResult(),
                c != null ? c.getVornxId() : null,
                c != null ? c.getFirstName() + " " + c.getLastName() : null,
                d.getPermit() != null ? d.getPermit().getPermitNumber() : null);
    }
}
