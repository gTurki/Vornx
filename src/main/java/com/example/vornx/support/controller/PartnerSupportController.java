package com.example.vornx.support.controller;

import com.example.vornx.audit.Audited;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.security.AuthUser;
import com.example.vornx.support.dto.MessageRequest;
import com.example.vornx.support.dto.OpenTicketRequest;
import com.example.vornx.support.dto.SupportTicketResponse;
import com.example.vornx.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Partner portal -> "الدعم": open a ticket, follow the conversation, reply. VIEWER can only read. */
@Tag(name = "Partner - Support", description = "Open tickets and talk to the VORNX team")
@RestController
@RequestMapping("/api/partner/support-tickets")
@RequiredArgsConstructor
public class PartnerSupportController {

    private static final String CAN_WRITE = "hasAnyRole('PARTNER_ADMIN','MANAGER','FRONT_DESK')";

    private final SupportService supportService;

    @GetMapping
    public PageResponse<SupportTicketResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                    @ParameterObject @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC)
                                                    Pageable pageable) {
        return supportService.listForPartner(AuthUser.from(jwt).partnerId(), pageable);
    }

    @GetMapping("/{ticketId}")
    public SupportTicketResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ticketId) {
        return supportService.getForPartner(AuthUser.from(jwt).partnerId(), ticketId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_WRITE)
    @Audited(action = "SUPPORT_TICKET_OPENED", target = "SUPPORT_TICKET", details = "#req.subject()")
    public SupportTicketResponse open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenTicketRequest req) {
        AuthUser me = AuthUser.from(jwt);
        return supportService.open(me.partnerId(), me.id(), req);
    }

    @PostMapping("/{ticketId}/messages")
    @PreAuthorize(CAN_WRITE)
    @Audited(action = "SUPPORT_REPLIED", target = "SUPPORT_TICKET")
    public SupportTicketResponse reply(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ticketId,
                                       @Valid @RequestBody MessageRequest req) {
        AuthUser me = AuthUser.from(jwt);
        return supportService.replyAsPartner(me.partnerId(), me.id(), ticketId, req.body());
    }
}
