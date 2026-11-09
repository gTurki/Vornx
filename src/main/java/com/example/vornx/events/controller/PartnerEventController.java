package com.example.vornx.events.controller;

import com.example.vornx.audit.Audited;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.events.dto.EventRequest;
import com.example.vornx.events.dto.EventResponse;
import com.example.vornx.events.dto.TicketResponse;
import com.example.vornx.events.dto.TicketUpdateRequest;
import com.example.vornx.events.service.EventService;
import com.example.vornx.events.service.TicketService;
import com.example.vornx.security.AuthUser;
import com.example.vornx.common.dto.RejectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Partner portal -> events: create, publish, cancel, and manage the guest list. */
@Tag(name = "Partner - Events", description = "Create and publish events, manage the guest list")
@RestController
@RequestMapping("/api/partner/events")
@RequiredArgsConstructor
public class PartnerEventController {

    private static final String CAN_EDIT = "hasAnyRole('PARTNER_ADMIN','MANAGER')";
    private static final String CAN_OPERATE = "hasAnyRole('PARTNER_ADMIN','MANAGER','FRONT_DESK')";

    private final EventService eventService;
    private final TicketService ticketService;

    @GetMapping
    public List<EventResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return eventService.listForPartner(partnerId(jwt));
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        return eventService.getForPartner(partnerId(jwt), eventId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    @Audited(action = "EVENT_CREATED", target = "EVENT", details = "#req.title()")
    public EventResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EventRequest req) {
        return eventService.create(partnerId(jwt), req);
    }

    @PutMapping("/{eventId}")
    @PreAuthorize(CAN_EDIT)
    @Audited(action = "EVENT_UPDATED", target = "EVENT")
    public EventResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId,
                                @Valid @RequestBody EventRequest req) {
        return eventService.update(partnerId(jwt), eventId, req);
    }

    @PostMapping("/{eventId}/publish")
    @PreAuthorize(CAN_EDIT)
    @Audited(action = "EVENT_PUBLISHED", target = "EVENT")
    public EventResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        return eventService.publish(partnerId(jwt), eventId);
    }

    @PostMapping("/{eventId}/cancel")
    @PreAuthorize(CAN_EDIT)
    @Audited(action = "EVENT_CANCELLED", target = "EVENT")
    public EventResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        return eventService.cancel(partnerId(jwt), eventId);
    }

    // ---------- Guest list ----------

    @GetMapping("/{eventId}/tickets")
    public List<TicketResponse> tickets(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        return ticketService.eventTickets(partnerId(jwt), eventId);
    }

    @PostMapping("/tickets/{ticketId}/approve")
    @PreAuthorize(CAN_OPERATE)
    @Audited(action = "EVENT_TICKET_APPROVED", target = "EVENT_TICKET", details = "#result.ticketNumber()")
    public TicketResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ticketId) {
        return ticketService.approve(partnerId(jwt), ticketId);
    }

    @PostMapping("/tickets/{ticketId}/reject")
    @PreAuthorize(CAN_OPERATE)
    @Audited(action = "EVENT_TICKET_REJECTED", target = "EVENT_TICKET", details = "#req.reason()")
    public TicketResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ticketId,
                                 @Valid @RequestBody RejectRequest req) {
        return ticketService.reject(partnerId(jwt), ticketId, req.reason());
    }

    @PatchMapping("/tickets/{ticketId}")
    @PreAuthorize(CAN_OPERATE)
    @Audited(action = "EVENT_TICKET_UPDATED", target = "EVENT_TICKET")
    public TicketResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ticketId,
                                 @Valid @RequestBody TicketUpdateRequest req) {
        return ticketService.update(partnerId(jwt), ticketId, req);
    }

    private static Long partnerId(Jwt jwt) {
        return AuthUser.from(jwt).partnerId();
    }
}
