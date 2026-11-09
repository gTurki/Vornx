package com.example.vornx.partner.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.partner.dto.PartnerUserResponse;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Partner - Profile", description = "The logged-in partner staff member")
@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class PartnerProfileController {

    private final PartnerUserService partnerUserService;

    /** GET /api/partner/me -> the logged-in partner staff member and their organization. */
    @GetMapping("/me")
    public PartnerUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return partnerUserService.getProfile(AuthUser.from(jwt).id());
    }
}
