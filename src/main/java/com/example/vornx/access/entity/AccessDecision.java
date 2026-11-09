package com.example.vornx.access.entity;

import com.example.vornx.access.enums.AccessMethod;
import com.example.vornx.access.enums.Decision;
import com.example.vornx.access.enums.DenyReason;
import com.example.vornx.access.enums.Direction;
import com.example.vornx.access.enums.EnforcementResult;
import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.customer.entity.Card;
import com.example.vornx.customer.entity.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One attempt to open something, ALLOWED or DENIED. Never deleted: it's the audit trail. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "access_decisions")
public class AccessDecision extends BaseEntity {

    @Column(name = "decision_number", nullable = false, length = 20, updatable = false)
    private String decisionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_point_id", nullable = false, updatable = false)
    private AccessPoint accessPoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", updatable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permit_id", updatable = false)
    private Permit permit;

    /** Which reader asked. Kept as a plain id (no relationship needed). */
    @Column(name = "api_key_id", updatable = false)
    private Long apiKeyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccessMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Decision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "deny_reason", length = 40, updatable = false)
    private DenyReason denyReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "enforcement_result", length = 20)
    private EnforcementResult enforcementResult;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public AccessDecision(String decisionNumber, AccessPoint accessPoint, Customer customer, Card card, Permit permit,
                          Long apiKeyId, AccessMethod method, Direction direction, Decision decision,
                          DenyReason denyReason, Instant occurredAt) {
        this.decisionNumber = decisionNumber;
        this.accessPoint = accessPoint;
        this.customer = customer;
        this.card = card;
        this.permit = permit;
        this.apiKeyId = apiKeyId;
        this.method = method;
        this.direction = direction;
        this.decision = decision;
        this.denyReason = denyReason;
        this.occurredAt = occurredAt;
    }
}
