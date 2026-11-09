package com.example.vornx.support.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.support.enums.TicketCategory;
import com.example.vornx.support.enums.TicketPriority;
import com.example.vornx.support.enums.TicketStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A support conversation between a partner and the VORNX team. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "support_tickets")
public class SupportTicket extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @Column(name = "opened_by_partner_user_id", updatable = false)
    private Long openedByPartnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketCategory category;

    @Column(nullable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private AdminUser assignedAdmin;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    /**
     * cascade = ALL: saving the ticket also saves new messages added to this list.
     * This is the "aggregate" pattern: messages are only ever added THROUGH the ticket (addMessage).
     */
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TicketMessage> messages = new ArrayList<>();

    public SupportTicket(String number, Partner partner, Long openedByPartnerUserId, TicketCategory category,
                         String subject, TicketPriority priority) {
        this.number = number;
        this.partner = partner;
        this.openedByPartnerUserId = openedByPartnerUserId;
        this.category = category;
        this.subject = subject;
        this.priority = priority;
        this.status = TicketStatus.OPEN;
    }

    public void addMessage(TicketMessage message, Instant at) {
        message.attachTo(this);
        messages.add(message);
        lastMessageAt = at;
    }
}
