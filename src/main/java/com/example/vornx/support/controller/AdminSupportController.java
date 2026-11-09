package com.example.vornx.support.controller;

import com.example.vornx.audit.Audited;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.security.AuthUser;
import com.example.vornx.support.dto.AdminTicketUpdateRequest;
import com.example.vornx.support.dto.MessageRequest;
import com.example.vornx.support.dto.SupportTicketResponse;
import com.example.vornx.support.enums.TicketPriority;
import com.example.vornx.support.enums.TicketStatus;
import com.example.vornx.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin dashboard -> "الدعم": the support inbox. AUDITOR can read only. */
@Tag(name = "Admin - Support inbox", description = "Answer, assign and close partner tickets")
@RestController
@RequestMapping("/api/admin/support-tickets")
@RequiredArgsConstructor
public class AdminSupportController {

    private static final String CAN_ANSWER = "hasAnyRole('SUPER_ADMIN','ACCOUNT_MANAGER','SUPPORT_AGENT')";

    private final SupportService supportService;

    /** GET /api/admin/support-tickets?status=OPEN&priority=HIGH&mine=true */
    @GetMapping
    public PageResponse<SupportTicketResponse> search(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(required = false) TicketStatus status,
                                                      @RequestParam(required = false) TicketPriority priority,
                                                      @RequestParam(required = false) Long partnerId,
                                                      @RequestParam(defaultValue = "false") boolean mine,
                                                      @ParameterObject @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC)
                                                      Pageable pageable) {
        Long assigned = mine ? AuthUser.from(jwt).id() : null;
        return supportService.search(status, priority, partnerId, assigned, pageable);
    }

    @GetMapping("/{ticketId}")
    public SupportTicketResponse get(@PathVariable Long ticketId) {
        return supportService.getForAdmin(ticketId);
    }

    @PostMapping("/{ticketId}/messages")
    @PreAuthorize(CAN_ANSWER)
    @Audited(action = "SUPPORT_REPLIED", target = "SUPPORT_TICKET", partnerId = "#result.partnerId()")
    public SupportTicketResponse reply(@AuthenticationPrincipal Jwt jwt, @PathVariable Long ticketId,
                                       @Valid @RequestBody MessageRequest req) {
        return supportService.replyAsAdmin(AuthUser.from(jwt).id(), ticketId, req.body());
    }

    @PatchMapping("/{ticketId}")
    @PreAuthorize(CAN_ANSWER)
    @Audited(action = "SUPPORT_TICKET_UPDATED", target = "SUPPORT_TICKET", partnerId = "#result.partnerId()", details = "'status=' + #result.status() + ', priority=' + #result.priority()")
    public SupportTicketResponse update(@PathVariable Long ticketId, @RequestBody AdminTicketUpdateRequest req) {
        return supportService.update(ticketId, req);
    }
}
