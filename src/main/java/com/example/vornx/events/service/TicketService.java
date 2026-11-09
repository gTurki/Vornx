package com.example.vornx.events.service;
import java.util.Map;

import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;

import com.example.vornx.access.entity.Permit;
import com.example.vornx.access.enums.PermitStatus;
import com.example.vornx.access.enums.PermitType;
import com.example.vornx.access.event.AccessGrantedEvent;
import com.example.vornx.access.service.PermitService;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.RandomCodes;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.events.dto.TicketResponse;
import com.example.vornx.events.dto.TicketUpdateRequest;
import com.example.vornx.events.entity.Event;
import com.example.vornx.events.entity.EventTicket;
import com.example.vornx.events.enums.EventStatus;
import com.example.vornx.events.enums.EventTicketStatus;
import com.example.vornx.events.enums.ReminderOption;
import com.example.vornx.events.repository.EventRepository;
import com.example.vornx.events.repository.EventTicketRepository;
import com.example.vornx.wallet.entity.WalletTransaction;
import com.example.vornx.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Tickets:  register (pay) -> UNDER_REVIEW -> partner approves -> CONFIRMED (+ EVENT_PASS permit) -> card tap -> CHECKED_IN
 *                                          \-> rejected / cancelled -> refund + permit revoked
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TicketService {

    /** Tickets that hold a place (count towards capacity). */
    static final Set<EventTicketStatus> ACTIVE = EnumSet.of(
            EventTicketStatus.UNDER_REVIEW, EventTicketStatus.CONFIRMED, EventTicketStatus.CHECKED_IN);

    private final EventRepository eventRepository;
    private final EventTicketRepository ticketRepository;
    private final CustomerService customerService;
    private final WalletService walletService;
    private final PermitService permitService;
    private final Clock clock;
    private final NotificationService notificationService;

    // =============== Customer ===============

    public TicketResponse register(Long customerId, Long eventId) {
        Instant now = Instant.now(clock);
        Event event = eventRepository.lockById(eventId) // lock: protects the last seats
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        if (!event.getStartsAt().isAfter(now)) {
            throw new BusinessException("REGISTRATION_CLOSED", "Registration is closed: the event has started");
        }
        if (ticketRepository.existsByEvent_IdAndCustomer_IdAndStatusIn(eventId, customerId, ACTIVE)) {
            throw new BusinessException("ALREADY_REGISTERED", "You already have a ticket for this event");
        }
        if (event.getCapacity() != null && ticketRepository.countByEvent_IdAndStatusIn(eventId, ACTIVE) >= event.getCapacity()) {
            throw new BusinessException("EVENT_FULL", "This event is full");
        }

        Customer customer = customerService.getEntity(customerId);
        WalletTransaction payment = event.getFee().signum() > 0
                ? walletService.charge(customerId, event.getFee(), event.getPartner(), "Event ticket: " + event.getTitle())
                : null;
        EventTicket ticket = ticketRepository.save(new EventTicket(newTicketNumber(), event, customer, payment));
        if (!event.isRequiresApproval()) {
            confirm(ticket);
        } else {
            notificationService.notifyPartner(event.getPartner().getId(), NotificationType.NEW_EVENT_REGISTRATION,
                    Map.of("customer", customer.getFirstName() + " " + customer.getLastName(), "event", event.getTitle()),
                    NotificationService.Ref.of("EVENT_TICKET", ticket.getId()));
        }
        return TicketResponse.from(ticket, now);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> myTickets(Long customerId) {
        Instant now = Instant.now(clock);
        return ticketRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId).stream()
                .map(t -> TicketResponse.from(t, now))
                .toList();
    }

    public TicketResponse cancelByCustomer(Long customerId, Long ticketId) {
        EventTicket ticket = ticketRepository.lockForCustomer(ticketId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        Instant now = Instant.now(clock);
        if (ticket.getStatus() != EventTicketStatus.UNDER_REVIEW && ticket.getStatus() != EventTicketStatus.CONFIRMED) {
            throw new BusinessException("CANNOT_CANCEL", "This ticket cannot be cancelled");
        }
        if (!ticket.getEvent().getStartsAt().isAfter(now)) {
            throw new BusinessException("CANNOT_CANCEL", "The event has already started");
        }
        cancelAndRefund(ticket, "Ticket cancelled: ");
        return TicketResponse.from(ticket, now);
    }

    public TicketResponse setReminder(Long customerId, Long ticketId, ReminderOption reminder) {
        EventTicket ticket = ticketRepository.lockForCustomer(ticketId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        ticket.setReminder(reminder);
        ticket.setReminderSentAt(null); // a new reminder choice can be sent again (EventReminderJob)
        return TicketResponse.from(ticket, Instant.now(clock));
    }

    // =============== Partner ===============

    @Transactional(readOnly = true)
    public List<TicketResponse> eventTickets(Long partnerId, Long eventId) {
        eventRepository.findByIdAndPartner_Id(eventId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        Instant now = Instant.now(clock);
        return ticketRepository.findByEvent_IdOrderByCreatedAt(eventId).stream()
                .map(t -> TicketResponse.from(t, now))
                .toList();
    }

    public TicketResponse approve(Long partnerId, Long ticketId) {
        EventTicket ticket = lockUnderReview(partnerId, ticketId);
        confirm(ticket);
        return TicketResponse.from(ticket, Instant.now(clock));
    }

    public TicketResponse reject(Long partnerId, Long ticketId, String reason) {
        EventTicket ticket = lockUnderReview(partnerId, ticketId);
        ticket.setRejectionReason(reason.trim());
        ticket.setRefundTransaction(refundIfPaid(ticket, "Ticket rejected: "));
        ticket.setStatus(EventTicketStatus.REJECTED);
        notificationService.notifyCustomer(ticket.getCustomer().getId(), NotificationType.TICKET_REJECTED,
                Map.of("event", ticket.getEvent().getTitle(), "reason", reason.trim()),
                NotificationService.Ref.of("EVENT_TICKET", ticket.getId()));
        return TicketResponse.from(ticket, Instant.now(clock));
    }

    public TicketResponse update(Long partnerId, Long ticketId, TicketUpdateRequest req) {
        EventTicket ticket = ticketRepository.lockForPartner(ticketId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (req.role() != null) {
            ticket.setRole(req.role());
        }
        if (req.passType() != null) {
            ticket.setPassType(req.passType().isBlank() ? null : req.passType().trim());
        }
        if (req.seat() != null) {
            ticket.setSeat(req.seat().isBlank() ? null : req.seat().trim());
        }
        return TicketResponse.from(ticket, Instant.now(clock));
    }

    // =============== Used by EventService ===============

    /** Event cancelled -> every ticket refunded and its permit revoked. */
    void cancelAllForEvent(Event event) {
        for (EventTicket t : ticketRepository.findByEvent_IdAndStatusIn(event.getId(), ACTIVE)) {
            cancelAndRefund(t, "Event cancelled: ");
            notificationService.notifyCustomer(t.getCustomer().getId(), NotificationType.EVENT_CANCELLED,
                    Map.of("event", event.getTitle()), NotificationService.Ref.of("EVENT_TICKET", t.getId()));
        }
    }

    /** Event dates changed -> move the permits of confirmed tickets too. */
    void syncPermitTimes(Event event) {
        for (EventTicket t : ticketRepository.findByEvent_IdAndStatusIn(event.getId(), ACTIVE)) {
            Permit p = t.getPermit();
            if (p != null && p.getStatus() != PermitStatus.REVOKED) {
                p.setValidFrom(event.accessFrom());
                p.setValidUntil(event.getEndsAt());
            }
        }
    }

    // =============== Automatic check-in ===============

    /** The access engine allowed a door: if that permit belongs to a ticket, the guest is now checked in. */
    @EventListener
    public void onAccessGranted(AccessGrantedEvent e) {
        ticketRepository.findByPermit_Id(e.permitId())
                .filter(t -> t.getStatus() == EventTicketStatus.CONFIRMED)
                .ifPresent(t -> {
                    t.setStatus(EventTicketStatus.CHECKED_IN);
                    t.setCheckedInAt(e.occurredAt());
                    log.info("Ticket {} checked in", t.getTicketNumber());
                });
    }

    // =============== helpers ===============

    private void confirm(EventTicket ticket) {
        Event event = ticket.getEvent();
        Permit permit = permitService.issueFromTemplate(ticket.getCustomer(), event.getSite(), event.getAccessPoints(),
                PermitType.EVENT_PASS, event.accessFrom(), event.getEndsAt(), Set.of(), null, null,
                "Ticket " + ticket.getTicketNumber() + " - " + event.getTitle());
        ticket.setPermit(permit);
        ticket.setStatus(EventTicketStatus.CONFIRMED);
        notificationService.notifyCustomer(ticket.getCustomer().getId(), NotificationType.TICKET_CONFIRMED,
                Map.of("event", event.getTitle(), "startsAt", event.getStartsAt()),
                NotificationService.Ref.of("EVENT_TICKET", ticket.getId()));
    }

    private void cancelAndRefund(EventTicket ticket, String refundPrefix) {
        ticket.setRefundTransaction(refundIfPaid(ticket, refundPrefix));
        if (ticket.getPermit() != null) {
            ticket.getPermit().setStatus(PermitStatus.REVOKED);
        }
        ticket.setStatus(EventTicketStatus.CANCELLED);
    }

    private WalletTransaction refundIfPaid(EventTicket ticket, String prefix) {
        WalletTransaction payment = ticket.getPaymentTransaction();
        if (payment == null) {
            return null;
        }
        return walletService.refund(ticket.getCustomer().getId(), payment.getAmount(), ticket.getEvent().getPartner(),
                prefix + ticket.getTicketNumber());
    }

    private EventTicket lockUnderReview(Long partnerId, Long ticketId) {
        EventTicket ticket = ticketRepository.lockForPartner(ticketId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (ticket.getStatus() != EventTicketStatus.UNDER_REVIEW) {
            throw new BusinessException("TICKET_NOT_UNDER_REVIEW", "This ticket was already " + ticket.getStatus());
        }
        return ticket;
    }

    private String newTicketNumber() {
        String n;
        do {
            n = "TCK-" + RandomCodes.alphanumeric(8);
        } while (ticketRepository.existsByTicketNumber(n));
        return n;
    }
}
