package com.example.vornx.partner.controller;

import com.example.vornx.audit.Audited;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.partner.dto.InvitePartnerUserRequest;
import com.example.vornx.partner.dto.PartnerUserInviteResponse;
import com.example.vornx.partner.dto.PartnerUserResponse;
import com.example.vornx.partner.dto.PartnerUserUpdateRequest;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Partner portal -> "الفريق والصلاحيات" (team & permissions).
 * The partnerId ALWAYS comes from the token, never from the URL, so a hotel can only see its own team.
 */
@Tag(name = "Partner - Team", description = "Invite staff, change roles, suspend")
@RestController
@RequestMapping("/api/partner/team")
@RequiredArgsConstructor
public class PartnerTeamController {

    private final PartnerUserService partnerUserService;

    @GetMapping
    public List<PartnerUserResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return partnerUserService.listTeam(AuthUser.from(jwt).partnerId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PARTNER_ADMIN')")
    @Audited(action = "TEAM_MEMBER_INVITED", target = "PARTNER_USER", targetId = "#result.user().id()", details = "#req.email() + ' as ' + #req.role()")
    public PartnerUserInviteResponse invite(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody InvitePartnerUserRequest req) {
        return partnerUserService.invite(AuthUser.from(jwt).partnerId(), req);
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('PARTNER_ADMIN')")
    @Audited(action = "TEAM_MEMBER_UPDATED", target = "PARTNER_USER", details = "'role=' + #result.role() + ', status=' + #result.status()")
    public PartnerUserResponse update(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable Long userId,
                                      @RequestBody PartnerUserUpdateRequest req) {
        AuthUser me = AuthUser.from(jwt);
        return partnerUserService.updateTeamMember(me.partnerId(), userId, me.id(), req);
    }
}
