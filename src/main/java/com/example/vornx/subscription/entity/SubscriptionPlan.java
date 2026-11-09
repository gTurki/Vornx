package com.example.vornx.subscription.entity;

import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.entity.Site;
import com.example.vornx.access.enums.PermitType;
import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.subscription.enums.BillingPeriod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Something a partner sells, e.g. "Fitness Plus - annual membership, 2,400 SAR".
 * It's also a TEMPLATE: when a subscription is approved, the permit is built from these fields.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "permit_type", nullable = false, length = 30)
    private PermitType permitType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 20)
    private BillingPeriod billingPeriod;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    /** false = activated instantly after payment (e.g. a transit pass). */
    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "subscription_plan_days", joinColumns = @JoinColumn(name = "plan_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private Set<DayOfWeek> allowedDays = new HashSet<>();

    @Column(name = "allowed_from")
    private LocalTime allowedFrom;

    @Column(name = "allowed_to")
    private LocalTime allowedTo;

    @Column(nullable = false)
    private boolean active;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "subscription_plan_access_points",
            joinColumns = @JoinColumn(name = "plan_id"),
            inverseJoinColumns = @JoinColumn(name = "access_point_id"))
    private Set<AccessPoint> accessPoints = new HashSet<>();

    public SubscriptionPlan(Partner partner, Site site, String name) {
        this.partner = partner;
        this.site = site;
        this.name = name;
        this.active = true;
        this.requiresApproval = true;
    }
}
