package com.example.vornx.subscription.entity;

import com.example.vornx.access.entity.Permit;
import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.subscription.enums.SubscriptionStatus;
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

/** A customer's subscription to a plan. While ACTIVE, its permit opens the doors. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "subscriptions")
public class Subscription extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private SubscriptionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permit_id")
    private Permit permit;

    public static Subscription pending(String number, Customer customer, SubscriptionPlan plan) {
        Subscription s = new Subscription();
        s.number = number;
        s.customer = customer;
        s.plan = plan;
        s.partner = plan.getPartner();
        s.status = SubscriptionStatus.PENDING;
        return s;
    }

    public SubscriptionStatus effectiveStatus(Instant now) {
        if (status == SubscriptionStatus.ACTIVE && endAt != null && !now.isBefore(endAt)) {
            return SubscriptionStatus.EXPIRED;
        }
        return status;
    }
}
