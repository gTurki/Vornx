package com.example.vornx.subscription.controller;

import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.security.AuthUser;
import com.example.vornx.subscription.dto.PlanResponse;
import com.example.vornx.subscription.dto.ServiceRequestResponse;
import com.example.vornx.subscription.dto.SubscribeRequest;
import com.example.vornx.subscription.dto.SubscriptionResponse;
import com.example.vornx.subscription.service.PlanService;
import com.example.vornx.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customer app (index_76) -> browse plans, subscribe, renew, follow requests. */
@Tag(name = "Customer - Subscriptions", description = "Browse plans, subscribe, renew, cancel requests")
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerSubscriptionController {

    private final PlanService planService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public PageResponse<PlanResponse> catalog(@RequestParam(required = false) Long partnerId,
                                              @RequestParam(required = false) Long siteId,
                                              @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return planService.catalog(partnerId, siteId, pageable);
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionResponse> mySubscriptions(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.mySubscriptions(AuthUser.from(jwt).id());
    }

    /** Pays from the wallet and sends an activation request. */
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRequestResponse subscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubscribeRequest req) {
        return subscriptionService.subscribe(AuthUser.from(jwt).id(), req.planId());
    }

    @PostMapping("/subscriptions/{subscriptionId}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRequestResponse renew(@AuthenticationPrincipal Jwt jwt, @PathVariable Long subscriptionId) {
        return subscriptionService.renew(AuthUser.from(jwt).id(), subscriptionId);
    }

    @GetMapping("/subscription-requests")
    public List<ServiceRequestResponse> myRequests(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.myRequests(AuthUser.from(jwt).id());
    }

    @PostMapping("/subscription-requests/{requestId}/cancel")
    public ServiceRequestResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId) {
        return subscriptionService.cancel(AuthUser.from(jwt).id(), requestId);
    }
}
