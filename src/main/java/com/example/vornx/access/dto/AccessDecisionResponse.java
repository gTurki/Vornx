package com.example.vornx.access.dto;

import com.example.vornx.access.entity.AccessDecision;
import com.example.vornx.access.enums.Decision;
import com.example.vornx.access.enums.DenyReason;
import com.example.vornx.access.enums.EnforcementResult;

import java.time.Instant;

/** The answer to "can this person open this?" - sent to the reader or the customer app. */
public record AccessDecisionResponse(
        String decisionNumber,
        Decision decision,
        DenyReason denyReason,
        Instant occurredAt,
        Long accessPointId,
        String accessPointName,
        String permitNumber,
        EnforcementResult enforcementResult
) {
    public static AccessDecisionResponse from(AccessDecision d) {
        return new AccessDecisionResponse(d.getDecisionNumber(), d.getDecision(), d.getDenyReason(), d.getOccurredAt(),
                d.getAccessPoint().getId(), d.getAccessPoint().getName(),
                d.getPermit() != null ? d.getPermit().getPermitNumber() : null,
                d.getEnforcementResult());
    }
}
