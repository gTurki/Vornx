package com.example.vornx.platform.team;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.enums.AdminRole;
import com.example.vornx.platform.repository.AdminUserRepository;
import com.example.vornx.security.UserType;
import com.example.vornx.security.session.SessionService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * "فريق VORNX": a SUPER_ADMIN invites colleagues, changes their role, suspends them.
 * Rules: nobody changes themselves, and there is always at least one active SUPER_ADMIN (nobody can lock the team out).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTeamService {

    /** Shorter than partner invites: this link opens the whole platform. */
    static final Duration INVITE_VALIDITY = Duration.ofDays(3);

    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final EmailService email;
    private final EmailTemplates templates;
    private final Clock clock;

    @Value("${vornx.dev.expose-tokens:false}")
    private boolean exposeTokens;

    public record InviteRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 120) String email,
            @NotNull AdminRole role) {
    }

    /** Both optional: send only what changes. */
    public record UpdateRequest(AdminRole role, AccountStatus status) {
    }

    public record TeamMember(Long id, String fullName, String email, AdminRole role, AccountStatus status,
                             Instant lastLoginAt, Instant inviteExpiresAt, Instant createdAt) {
        static TeamMember from(AdminUser u) {
            return new TeamMember(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getStatus(),
                    u.getLastLoginAt(), u.getInviteExpiresAt(), u.getCreatedAt());
        }
    }

    /** inviteToken is only filled in development (expose-tokens), to test without an inbox. */
    public record InviteResponse(TeamMember member, String inviteToken) {
    }

    public List<TeamMember> list() {
        return admins.findAllByOrderByCreatedAtAsc().stream().map(TeamMember::from).toList();
    }

    @Transactional
    public InviteResponse invite(InviteRequest req, Long actingAdminId) {
        String address = req.email().trim().toLowerCase();
        if (admins.existsByEmail(address)) {
            throw new BusinessException("EMAIL_TAKEN", "This email is already on the VORNX team");
        }
        String raw = SecureTokens.generate();
        AdminUser u = admins.save(AdminUser.invite(req.fullName().trim(), address, req.role(),
                SecureTokens.sha256(raw), Instant.now(clock).plus(INVITE_VALIDITY)));
        sendInvite(u, raw, actingAdminId);
        return new InviteResponse(TeamMember.from(u), exposeTokens ? raw : null);
    }

    /** The link expired or got lost: a new link, and the old one stops working. */
    @Transactional
    public InviteResponse resendInvite(Long id, Long actingAdminId) {
        AdminUser u = get(id);
        if (u.getStatus() != AccountStatus.INVITED) {
            throw new BusinessException("NOT_INVITED", "This person already accepted, or the invite was cancelled");
        }
        String raw = SecureTokens.generate();
        u.reissueInvite(SecureTokens.sha256(raw), Instant.now(clock).plus(INVITE_VALIDITY));
        sendInvite(u, raw, actingAdminId);
        return new InviteResponse(TeamMember.from(u), exposeTokens ? raw : null);
    }

    @Transactional
    public TeamMember update(Long id, Long actingAdminId, UpdateRequest req) {
        if (id.equals(actingAdminId)) {
            throw new BusinessException("CANNOT_CHANGE_SELF", "You cannot change your own role or status");
        }
        AdminUser u = get(id);
        boolean roleChanges = req.role() != null && req.role() != u.getRole();
        boolean statusChanges = req.status() != null && req.status() != u.getStatus();
        boolean losesSuperAdmin = u.getRole() == AdminRole.SUPER_ADMIN && u.getStatus() == AccountStatus.ACTIVE
                && (roleChanges || statusChanges);
        if (losesSuperAdmin && admins.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new BusinessException("LAST_SUPER_ADMIN", "The team needs at least one active super admin");
        }

        if (roleChanges) {
            u.setRole(req.role());
        }
        if (statusChanges) {
            switch (req.status()) {
                case INVITED -> throw new BusinessException("INVALID_STATUS", "Status can only be ACTIVE or SUSPENDED");
                case SUSPENDED -> u.suspend();
                case ACTIVE -> {
                    if (u.getPasswordHash() == null) { // never accepted: send a new invite instead
                        throw new BusinessException("INVITE_PENDING", "This person has not set a password yet. Send a new invite.");
                    }
                    u.setStatus(AccountStatus.ACTIVE);
                }
            }
        }
        if (roleChanges || u.getStatus() == AccountStatus.SUSPENDED) {
            // applies now: their sessions end, so they log in again with the new rights (or not at all)
            sessions.revokeAll(UserType.ADMIN, u.getId(), null);
        }
        return TeamMember.from(u);
    }

    /** The invited person opens the email link and chooses a password. */
    @Transactional
    public AdminUser acceptInvite(String rawToken, String password) {
        AdminUser u = admins.findByInviteTokenHash(SecureTokens.sha256(rawToken))
                .filter(a -> a.getStatus() == AccountStatus.INVITED)
                .orElseThrow(() -> new BusinessException("INVITE_INVALID", "This invite link is invalid or already used"));
        if (u.getInviteExpiresAt().isBefore(Instant.now(clock))) {
            throw new BusinessException("INVITE_EXPIRED", "This invite link has expired. Ask for a new one.");
        }
        u.acceptInvite(passwordEncoder.encode(password));
        u.setLastLoginAt(Instant.now(clock));
        return u;
    }

    private void sendInvite(AdminUser u, String rawToken, Long actingAdminId) {
        String inviter = admins.findById(actingAdminId).map(AdminUser::getFullName).orElse("VORNX");
        email.queue(u.getEmail(), EmailType.ADMIN_INVITE,
                templates.adminInvite(u.getFullName(), inviter, u.getRole().arabic(), u.getRole().english(), rawToken));
    }

    private AdminUser get(Long id) {
        return admins.findById(id).orElseThrow(() -> new ResourceNotFoundException("Admin", id));
    }
}
