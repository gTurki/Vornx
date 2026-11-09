package com.example.vornx.partner.controller;

import com.example.vornx.audit.Audited;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.partner.dto.ApiKeyCreatedResponse;
import com.example.vornx.partner.dto.ApiKeyRequest;
import com.example.vornx.partner.dto.ApiKeyResponse;
import com.example.vornx.partner.service.ApiKeyService;
import com.example.vornx.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/** Partner portal -> integrations: keys for their readers and locks. PARTNER_ADMIN only. */
@Tag(name = "Partner - API keys", description = "Keys for readers and locks (PARTNER_ADMIN only)")
@RestController
@RequestMapping("/api/partner/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARTNER_ADMIN')")
public class PartnerApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public List<ApiKeyResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return apiKeyService.list(AuthUser.from(jwt).partnerId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Audited(action = "API_KEY_CREATED", target = "API_KEY", targetId = "#result.key().id()", details = "#req.name()")
    public ApiKeyCreatedResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ApiKeyRequest req) {
        return apiKeyService.create(AuthUser.from(jwt).partnerId(), req.name());
    }

    @PostMapping("/{keyId}/revoke")
    @Audited(action = "API_KEY_REVOKED", target = "API_KEY")
    public ApiKeyResponse revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long keyId) {
        return apiKeyService.revoke(AuthUser.from(jwt).partnerId(), keyId);
    }
}
