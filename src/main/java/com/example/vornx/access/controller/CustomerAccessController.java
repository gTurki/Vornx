package com.example.vornx.access.controller;

import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.access.dto.AccessDecisionResponse;
import com.example.vornx.access.dto.AccessEventResponse;
import com.example.vornx.access.dto.PermitResponse;
import com.example.vornx.access.enums.Direction;
import com.example.vornx.access.service.AccessDecisionService;
import com.example.vornx.access.service.PermitService;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customer app (index_76) -> "التصاريح", remote open, and "سجل الدخول والخروج". */
@Tag(name = "Customer - Permits & access", description = "My permits, remote open, entry and exit log")
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerAccessController {

    private final PermitService permitService;
    private final AccessDecisionService accessDecisionService;

    @GetMapping("/permits")
    public List<PermitResponse> myPermits(@AuthenticationPrincipal Jwt jwt) {
        return permitService.listForCustomer(AuthUser.from(jwt).id());
    }

    @GetMapping("/permits/{permitId}")
    public PermitResponse myPermit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long permitId) {
        return permitService.getForCustomer(AuthUser.from(jwt).id(), permitId);
    }

    /** "افتح الباب عن بُعد" - POST /api/customer/access-points/1/open?direction=ENTRY */
    @PostMapping("/access-points/{accessPointId}/open")
    public AccessDecisionResponse openRemotely(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable Long accessPointId,
                                               @RequestParam(required = false) Direction direction) {
        return accessDecisionService.decideRemote(AuthUser.from(jwt).id(), accessPointId, direction);
    }

    @GetMapping("/access-log")
    public PageResponse<AccessEventResponse> myLog(@AuthenticationPrincipal Jwt jwt,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
                                                   Pageable pageable) {
        return accessDecisionService.customerLog(AuthUser.from(jwt).id(), pageable);
    }
}
