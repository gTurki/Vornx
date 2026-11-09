package com.example.vornx.access.entity;

import com.example.vornx.access.enums.AccessLevel;
import com.example.vornx.access.enums.PermitStatus;
import com.example.vornx.access.enums.PermitType;
import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.partner.entity.Partner;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * One of the "many cards inside the one card".
 * Example: a hotel key = HOTEL_KEY permit, site = the hotel, accessPoints = {room 1204},
 *          validFrom = check-in, validUntil = check-out.
 */
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "permits")
public class Permit extends BaseEntity {

    @Column(name = "permit_number", nullable = false, length = 20, updatable = false)
    private String permitNumber;

    /** Short code the customer can show to staff to prove the permit is real. */
    @Column(name = "verification_code", nullable = false, length = 12, updatable = false)
    private String verificationCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false, updatable = false)
    private Site site;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PermitType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessLevel level;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    /** Empty = every day. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "permit_days", joinColumns = @JoinColumn(name = "permit_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private Set<DayOfWeek> allowedDays = new HashSet<>();

    /** Daily window in the business time zone. Both null = any time. */
    @Column(name = "allowed_from")
    private LocalTime allowedFrom;

    @Column(name = "allowed_to")
    private LocalTime allowedTo;

    @Column(length = 500)
    private String conditions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermitStatus status;

    /** Empty = every access point in the site. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "permit_access_points",
            joinColumns = @JoinColumn(name = "permit_id"),
            inverseJoinColumns = @JoinColumn(name = "access_point_id"))
    private Set<AccessPoint> accessPoints = new HashSet<>();

    public static Permit issue(String permitNumber, String verificationCode, Customer customer, Partner partner,
                               Site site, PermitType type, AccessLevel level, Instant validFrom, Instant validUntil) {
        Permit p = new Permit();
        p.permitNumber = permitNumber;
        p.verificationCode = verificationCode;
        p.customer = customer;
        p.partner = partner;
        p.site = site;
        p.type = type;
        p.level = level;
        p.validFrom = validFrom;
        p.validUntil = validUntil;
        p.status = PermitStatus.ACTIVE;
        return p;
    }

    /** What the user sees: the stored status, but with PENDING / EXPIRED worked out from the dates. */
    public PermitStatus effectiveStatus(Instant now) {
        if (status == PermitStatus.REVOKED || status == PermitStatus.SUSPENDED) {
            return status;
        }
        if (now.isBefore(validFrom)) {
            return PermitStatus.PENDING;
        }
        if (!now.isBefore(validUntil)) {
            return PermitStatus.EXPIRED;
        }
        return PermitStatus.ACTIVE;
    }

    public boolean coversWholeSite() {
        return accessPoints.isEmpty();
    }
}
