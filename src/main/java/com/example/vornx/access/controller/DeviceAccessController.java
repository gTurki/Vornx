package com.example.vornx.access.controller;

import com.example.vornx.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.access.dto.AccessDecisionResponse;
import com.example.vornx.access.dto.CardAccessRequest;
import com.example.vornx.access.dto.EnforcementResultRequest;
import com.example.vornx.access.service.AccessDecisionService;
import com.example.vornx.security.device.DevicePrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by readers and locks, not people. Authenticated with the "X-API-Key" header.
 * Flow:  card tapped -> POST /access-decisions -> ALLOW/DENY -> door opens (or not)
 *        -> POST /access-decisions/{number}/result  (SUCCESS / FAILED)
 */
@Tag(name = "Devices - Access decisions", description = "Called by readers and locks with the X-API-Key header")
@SecurityRequirement(name = OpenApiConfig.API_KEY) // readers use X-API-Key, not a JWT
@RestController
@RequestMapping("/api/devices/access-decisions")
@RequiredArgsConstructor
public class DeviceAccessController {

    private final AccessDecisionService accessDecisionService;

    @PostMapping
    public AccessDecisionResponse decide(@AuthenticationPrincipal DevicePrincipal device,
                                         @Valid @RequestBody CardAccessRequest req) {
        return accessDecisionService.decideByCard(device, req);
    }

    @PostMapping("/{decisionNumber}/result")
    public AccessDecisionResponse reportResult(@AuthenticationPrincipal DevicePrincipal device,
                                               @PathVariable String decisionNumber,
                                               @Valid @RequestBody EnforcementResultRequest req) {
        return accessDecisionService.reportResult(device, decisionNumber, req.result());
    }
}
