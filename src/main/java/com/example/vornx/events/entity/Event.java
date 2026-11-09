package com.example.vornx.events.entity;

import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.entity.Site;
import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.events.enums.EventStatus;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** An event or conference hosted by a partner at one of its sites ("مؤتمر التقنية"). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "events")
public class Event extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 80)
    private String category;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "doors_open_at")
    private Instant doorsOpenAt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal fee;

    /** null = unlimited */
    private Integer capacity;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "dress_code", length = 100)
    private String dressCode;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    /** @OrderColumn keeps the list in the order the partner entered it. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "event_agenda", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    private List<AgendaItem> agenda = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "event_access_points",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "access_point_id"))
    private Set<AccessPoint> accessPoints = new HashSet<>();

    public Event(Partner partner, Site site, String title) {
        this.partner = partner;
        this.site = site;
        this.title = title;
        this.fee = BigDecimal.ZERO;
        this.status = EventStatus.DRAFT;
    }

    /** When the ticket starts opening doors: doors-open time, or 1 hour before the start. */
    public Instant accessFrom() {
        return doorsOpenAt != null ? doorsOpenAt : startsAt.minus(Duration.ofHours(1));
    }

    public boolean isUpcoming(Instant now) {
        return endsAt.isAfter(now);
    }
}
