package com.example.vornx.support.service;
import java.util.Map;

import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;

import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.enums.ActorType;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.RandomCodes;
import com.example.vornx.partner.service.PartnerService;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.service.AdminUserService;
import com.example.vornx.support.dto.AdminTicketUpdateRequest;
import com.example.vornx.support.dto.OpenTicketRequest;
import com.example.vornx.support.dto.SupportTicketResponse;
import com.example.vornx.support.entity.SupportTicket;
import com.example.vornx.support.entity.TicketMessage;
import com.example.vornx.support.enums.TicketCategory;
import com.example.vornx.support.enums.TicketPriority;
import com.example.vornx.support.enums.TicketStatus;
import com.example.vornx.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class SupportService {

    private final SupportTicketRepository ticketRepository;
    private final PartnerService partnerService;
    private final PartnerUserService partnerUserService;
    private final AdminUserService adminUserService;
    private final Clock clock;
    private final NotificationService notificationService;

    // =============== Partner ===============

    public SupportTicketResponse open(Long partnerId, Long partnerUserId, OpenTicketRequest req) {
        String senderName = partnerUserService.getProfile(partnerUserId).fullName();
        SupportTicket ticket = create(partnerId, partnerUserId, req.category(), req.subject().trim(),
                req.priority() != null ? req.priority() : TicketPriority.MEDIUM,
                new TicketMessage(ActorType.PARTNER_USER, partnerUserId, senderName, req.message().trim()));
        return SupportTicketResponse.detail(ticket);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketResponse> listForPartner(Long partnerId, Pageable pageable) {
        return PageResponse.from(ticketRepository.findByPartner_Id(partnerId, pageable).map(SupportTicketResponse::summary));
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getForPartner(Long partnerId, Long ticketId) {
        return SupportTicketResponse.detail(getOwned(partnerId, ticketId));
    }

    /** A partner message on a CLOSED ticket re-opens it. */
    public SupportTicketResponse replyAsPartner(Long partnerId, Long partnerUserId, Long ticketId, String body) {
        SupportTicket ticket = getOwned(partnerId, ticketId);
        String name = partnerUserService.getProfile(partnerUserId).fullName();
        ticket.addMessage(new TicketMessage(ActorType.PARTNER_USER, partnerUserId, name, body.trim()), Instant.now(clock));
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            ticket.setStatus(TicketStatus.OPEN);
        }
        notificationService.notifyAdmins(NotificationType.SUPPORT_PARTNER_REPLY,
                Map.of("partner", ticket.getPartner().getName(), "ticket", ticket.getNumber(), "subject", ticket.getSubject()),
                NotificationService.Ref.of("SUPPORT_TICKET", ticket.getId()));
        return SupportTicketResponse.detail(ticket);
    }

    // =============== Other modules ===============

    /** Opened automatically by the system, e.g. "contract renewal request" from the billing module. */
    public SupportTicketResponse openFromSystem(Long partnerId, Long partnerUserId, TicketCategory category,
                                                String subject, String message) {
        String name = partnerUserId != null ? partnerUserService.getProfile(partnerUserId).fullName() : "VORNX";
        ActorType type = partnerUserId != null ? ActorType.PARTNER_USER : ActorType.SYSTEM;
        return SupportTicketResponse.detail(create(partnerId, partnerUserId, category, subject, TicketPriority.MEDIUM,
                new TicketMessage(type, partnerUserId, name, message)));
    }

    // =============== Admin ===============

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketResponse> search(TicketStatus status, TicketPriority priority, Long partnerId,
                                                      Long assignedAdminId, Pageable pageable) {
        return PageResponse.from(ticketRepository.search(status, priority, partnerId, assignedAdminId, pageable)
                .map(SupportTicketResponse::summary));
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getForAdmin(Long ticketId) {
        return SupportTicketResponse.detail(getAny(ticketId));
    }

    /** The first VORNX reply moves the ticket from OPEN to IN_PROGRESS. */
    public SupportTicketResponse replyAsAdmin(Long adminId, Long ticketId, String body) {
        SupportTicket ticket = getAny(ticketId);
        AdminUser admin = adminUserService.getEntity(adminId);
        ticket.addMessage(new TicketMessage(ActorType.ADMIN, adminId, admin.getFullName(), body.trim()), Instant.now(clock));
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        if (ticket.getAssignedAdmin() == null) {
            ticket.setAssignedAdmin(admin); // whoever answers first takes it
        }
        notificationService.notifyPartner(ticket.getPartner().getId(), NotificationType.SUPPORT_REPLY,
                Map.of("ticket", ticket.getNumber(), "subject", ticket.getSubject()),
                NotificationService.Ref.of("SUPPORT_TICKET", ticket.getId()));
        return SupportTicketResponse.detail(ticket);
    }

    public SupportTicketResponse update(Long ticketId, AdminTicketUpdateRequest req) {
        SupportTicket ticket = getAny(ticketId);
        if (req.status() != null) {
            ticket.setStatus(req.status());
        }
        if (req.priority() != null) {
            ticket.setPriority(req.priority());
        }
        if (req.assignedAdminId() != null) {
            ticket.setAssignedAdmin(req.assignedAdminId() == 0 ? null : adminUserService.getEntity(req.assignedAdminId()));
        }
        return SupportTicketResponse.detail(ticket);
    }

    @Transactional(readOnly = true)
    public long countUnresolved() {
        return ticketRepository.countByStatusNot(TicketStatus.CLOSED);
    }

    // =============== helpers ===============

    private SupportTicket create(Long partnerId, Long partnerUserId, TicketCategory category, String subject,
                                 TicketPriority priority, TicketMessage firstMessage) {
        String number;
        do {
            number = "SUP-" + RandomCodes.digits(6);
        } while (ticketRepository.existsByNumber(number));
        SupportTicket ticket = new SupportTicket(number, partnerService.getEntity(partnerId), partnerUserId,
                category, subject, priority);
        ticket.addMessage(firstMessage, Instant.now(clock));
        ticketRepository.save(ticket); // cascade also saves the first message
        notificationService.notifyAdmins(NotificationType.NEW_SUPPORT_TICKET,
                Map.of("partner", ticket.getPartner().getName(), "subject", subject, "priority", priority),
                NotificationService.Ref.of("SUPPORT_TICKET", ticket.getId()));
        return ticket;
    }

    private SupportTicket getOwned(Long partnerId, Long ticketId) {
        return ticketRepository.findWithMessagesByIdAndPartner_Id(ticketId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket", ticketId));
    }

    private SupportTicket getAny(Long ticketId) {
        return ticketRepository.findWithMessagesById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket", ticketId));
    }
}
