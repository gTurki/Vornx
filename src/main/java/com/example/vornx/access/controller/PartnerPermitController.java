package com.example.vornx.access.controller;

import com.example.vornx.audit.Audited;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.access.dto.AccessEventResponse;
import com.example.vornx.access.dto.PermitExtendRequest;
import com.example.vornx.access.dto.PermitIssueRequest;
import com.example.vornx.access.dto.PermitResponse;
import com.example.vornx.access.service.AccessDecisionService;
import com.example.vornx.access.service.PermitService;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.security.AuthUser;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Partner portal -> "الضيوف والمفاتيح" (issue / revoke / extend permits) and "التشغيل المباشر" (live events).
 * FRONT_DESK can issue keys too: that is their daily job at a hotel.
 */
@Tag(name = "Partner - Permits & live events", description = "Issue, revoke and extend permits; live access events")
@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class PartnerPermitController {

    private static final String CAN_ISSUE = "hasAnyRole('PARTNER_ADMIN','MANAGER','FRONT_DESK')";

    private final PermitService permitService;
    private final AccessDecisionService accessDecisionService;

    @GetMapping("/permits")
    public PageResponse<PermitResponse> list(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false) Long siteId,
                                             @ParameterObject @PageableDefault(size = 20, sort = "validUntil", direction = Sort.Direction.DESC)
                                             Pageable pageable) {
        return permitService.listForPartner(partnerId(jwt), siteId, pageable);
    }

    @GetMapping("/permits/{permitId}")
    public PermitResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long permitId) {
        return permitService.getForPartner(partnerId(jwt), permitId);
    }

    /** "أرسل المفتاح" */
    @PostMapping("/permits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_ISSUE)
    @Audited(action = "PERMIT_ISSUED", target = "PERMIT", details = "#result.permitNumber() + ' for ' + #result.holderVornxId()")
    public PermitResponse issue(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PermitIssueRequest req) {
        return permitService.issue(partnerId(jwt), req);
    }

    /** "إلغاء المفتاح" */
    @PostMapping("/permits/{permitId}/revoke")
    @PreAuthorize(CAN_ISSUE)
    @Audited(action = "PERMIT_REVOKED", target = "PERMIT", details = "#result.permitNumber()")
    public PermitResponse revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long permitId) {
        return permitService.revoke(partnerId(jwt), permitId);
    }

    /** "تمديد" */
    @PatchMapping("/permits/{permitId}/extend")
    @PreAuthorize(CAN_ISSUE)
    @Audited(action = "PERMIT_EXTENDED", target = "PERMIT", details = "'until ' + #req.validUntil()")
    public PermitResponse extend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long permitId,
                                 @Valid @RequestBody PermitExtendRequest req) {
        return permitService.extend(partnerId(jwt), permitId, req);
    }

    /** "الأحداث المباشرة" - newest first. */
    @GetMapping("/access-events")
    public PageResponse<AccessEventResponse> events(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam(required = false) Long siteId,
                                                    @ParameterObject @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC)
                                                    Pageable pageable) {
        return accessDecisionService.partnerLog(partnerId(jwt), siteId, pageable);
    }

    private static Long partnerId(Jwt jwt) {
        return AuthUser.from(jwt).partnerId();
    }
}
