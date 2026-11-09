package com.example.vornx.events.controller;

import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.events.dto.EventResponse;
import com.example.vornx.events.dto.ReminderRequest;
import com.example.vornx.events.dto.TicketResponse;
import com.example.vornx.events.service.EventService;
import com.example.vornx.events.service.TicketService;
import com.example.vornx.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customer app (index_76) -> "الفعاليات": browse, register, my tickets, reminders. */
@Tag(name = "Customer - Events & tickets", description = "Browse events, register, my tickets and reminders")
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerEventController {

    private final EventService eventService;
    private final TicketService ticketService;

    @GetMapping("/events")
    public PageResponse<EventResponse> events(@ParameterObject @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.ASC)
                                              Pageable pageable) {
        return eventService.catalog(pageable);
    }

    @GetMapping("/events/{eventId}")
    public EventResponse event(@PathVariable Long eventId) {
        return eventService.getPublished(eventId);
    }

    @PostMapping("/events/{eventId}/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse register(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId) {
        return ticketService.register(AuthUser.from(jwt).id(), eventId);
    }

    @GetMapping("/tickets")
    public List<TicketResponse> myTickets(@AuthenticationPrincipal Jwt jwt) {
        return ticketService.myTickets(AuthUser.from(jwt).id());
    }

    @PostMapping("/tickets/{ticketId}/cancel")
    public TicketResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ticketId) {
        return ticketService.cancelByCustomer(AuthUser.from(jwt).id(), ticketId);
    }

    @PutMapping("/tickets/{ticketId}/reminder")
    public TicketResponse reminder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ticketId,
                                   @RequestBody ReminderRequest req) {
        return ticketService.setReminder(AuthUser.from(jwt).id(), ticketId, req.reminder());
    }
}
