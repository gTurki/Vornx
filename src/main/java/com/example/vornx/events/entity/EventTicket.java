package com.example.vornx.events.entity;

import com.example.vornx.access.entity.Permit;
import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.events.enums.AttendeeRole;
import com.example.vornx.events.enums.EventTicketStatus;
import com.example.vornx.events.enums.ReminderOption;
import com.example.vornx.wallet.entity.WalletTransaction;
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

/** "تذكرتك" - a customer's ticket. Once CONFIRMED it has an EVENT_PASS permit, so the card opens the venue. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event_tickets")
public class EventTicket extends BaseEntity {

    @Column(name = "ticket_number", nullable = false, length = 20, updatable = false)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendeeRole role;

    @Column(name = "pass_type", length = 50)
    private String passType;

    @Column(length = 30)
    private String seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventTicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReminderOption reminder;

    /** Set when the reminder was sent, so it is sent only once. */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permit_id")
    private Permit permit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id", updatable = false)
    private WalletTransaction paymentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_transaction_id")
    private WalletTransaction refundTransaction;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    public EventTicket(String ticketNumber, Event event, Customer customer, WalletTransaction payment) {
        this.ticketNumber = ticketNumber;
        this.event = event;
        this.customer = customer;
        this.paymentTransaction = payment;
        this.role = AttendeeRole.ATTENDEE;
        this.status = EventTicketStatus.UNDER_REVIEW;
    }
}
