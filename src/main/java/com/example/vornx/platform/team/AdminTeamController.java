package com.example.vornx.platform.team;

import com.example.vornx.audit.Audited;
import com.example.vornx.platform.team.AdminTeamService.InviteRequest;
import com.example.vornx.platform.team.AdminTeamService.InviteResponse;
import com.example.vornx.platform.team.AdminTeamService.TeamMember;
import com.example.vornx.platform.team.AdminTeamService.UpdateRequest;
import com.example.vornx.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Admin dashboard -> "فريق VORNX". Everyone on the team can see it; only a SUPER_ADMIN invites or changes people.
 * The invited person finishes at POST /api/public/auth/admin/accept-invite.
 */
@Tag(name = "Admin - Team", description = "Invite VORNX team members, change roles, suspend")
@RestController
@RequestMapping("/api/admin/team")
@RequiredArgsConstructor
public class AdminTeamController {

    private final AdminTeamService team;

    @GetMapping
    public List<TeamMember> list() {
        return team.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Audited(action = "ADMIN_INVITED", target = "ADMIN_USER", targetId = "#result.member().id()", details = "#req.email() + ' as ' + #req.role()")
    public InviteResponse invite(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InviteRequest req) {
        return team.invite(req, AuthUser.from(jwt).id());
    }

    @PostMapping("/{id}/resend-invite")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Audited(action = "ADMIN_INVITE_RESENT", target = "ADMIN_USER", targetId = "#id")
    public InviteResponse resend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return team.resendInvite(id, AuthUser.from(jwt).id());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Audited(action = "ADMIN_UPDATED", target = "ADMIN_USER", details = "'role=' + #result.role() + ', status=' + #result.status()")
    public TeamMember update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody UpdateRequest req) {
        return team.update(id, AuthUser.from(jwt).id(), req);
    }
}
