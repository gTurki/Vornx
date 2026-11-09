package com.example.vornx.subscription.controller;

import com.example.vornx.audit.Audited;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.security.AuthUser;
import com.example.vornx.subscription.dto.PlanRequest;
import com.example.vornx.subscription.dto.PlanResponse;
import com.example.vornx.common.dto.RejectRequest;
import com.example.vornx.subscription.dto.ServiceRequestResponse;
import com.example.vornx.subscription.dto.SubscriptionResponse;
import com.example.vornx.subscription.enums.RequestStatus;
import com.example.vornx.subscription.service.PlanService;
import com.example.vornx.subscription.service.SubscriptionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Partner portal -> plans they sell, and the requests waiting for approval.
 * Plans: PARTNER_ADMIN / MANAGER. Approving requests: also FRONT_DESK.
 */
@Tag(name = "Partner - Plans & subscriptions", description = "What the partner sells and the approval queue")
@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class PartnerSubscriptionController {

    private static final String CAN_EDIT_PLANS = "hasAnyRole('PARTNER_ADMIN','MANAGER')";
    private static final String CAN_DECIDE = "hasAnyRole('PARTNER_ADMIN','MANAGER','FRONT_DESK')";

    private final PlanService planService;
    private final SubscriptionService subscriptionService;

    // ---------- Plans ----------

    @GetMapping("/plans")
    public List<PlanResponse> plans(@AuthenticationPrincipal Jwt jwt) {
        return planService.listForPartner(AuthUser.from(jwt).partnerId());
    }

    @GetMapping("/plans/{planId}")
    public PlanResponse plan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        return planService.getForPartner(AuthUser.from(jwt).partnerId(), planId);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT_PLANS)
    @Audited(action = "PLAN_CREATED", target = "PLAN", details = "#req.name() + ' - ' + #req.price() + ' SAR'")
    public PlanResponse createPlan(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlanRequest req) {
        return planService.create(AuthUser.from(jwt).partnerId(), req);
    }

    @PutMapping("/plans/{planId}")
    @PreAuthorize(CAN_EDIT_PLANS)
    @Audited(action = "PLAN_UPDATED", target = "PLAN")
    public PlanResponse updatePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId,
                                   @Valid @RequestBody PlanRequest req) {
        return planService.update(AuthUser.from(jwt).partnerId(), planId, req);
    }

    // ---------- Requests & subscriptions ----------

    /** GET /api/partner/subscription-requests?status=PENDING */
    @GetMapping("/subscription-requests")
    public PageResponse<ServiceRequestResponse> requests(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam(required = false) RequestStatus status,
                                                         @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
                                                         Pageable pageable) {
        return subscriptionService.partnerRequests(AuthUser.from(jwt).partnerId(), status, pageable);
    }

    @PostMapping("/subscription-requests/{requestId}/approve")
    @PreAuthorize(CAN_DECIDE)
    @Audited(action = "SUBSCRIPTION_REQUEST_APPROVED", target = "SERVICE_REQUEST", details = "#result.requestNumber()")
    public ServiceRequestResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId) {
        AuthUser me = AuthUser.from(jwt);
        return subscriptionService.approve(me.partnerId(), me.id(), requestId);
    }

    @PostMapping("/subscription-requests/{requestId}/reject")
    @PreAuthorize(CAN_DECIDE)
    @Audited(action = "SUBSCRIPTION_REQUEST_REJECTED", target = "SERVICE_REQUEST", details = "#req.reason()")
    public ServiceRequestResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId,
                                         @Valid @RequestBody RejectRequest req) {
        AuthUser me = AuthUser.from(jwt);
        return subscriptionService.reject(me.partnerId(), me.id(), requestId, req.reason());
    }

    @GetMapping("/subscriptions")
    public PageResponse<SubscriptionResponse> subscriptions(@AuthenticationPrincipal Jwt jwt,
                                                            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                            Pageable pageable) {
        return subscriptionService.partnerSubscriptions(AuthUser.from(jwt).partnerId(), pageable);
    }
}
