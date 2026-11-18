package com.example.vornx.platform.team;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.email.FrontendProperties;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.enums.AdminRole;
import com.example.vornx.platform.repository.AdminUserRepository;
import com.example.vornx.platform.team.AdminTeamService.InviteRequest;
import com.example.vornx.platform.team.AdminTeamService.UpdateRequest;
import com.example.vornx.security.UserType;
import com.example.vornx.security.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "فريق VORNX": invites, and the rules that stop anyone locking the team out. Database = Mockito fakes. */
class AdminTeamRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private AdminUserRepository repo;
    private SessionService sessions;
    private EmailService email;
    private AdminTeamService service;
    private final AtomicReference<AdminUser> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        repo = mock(AdminUserRepository.class);
        sessions = mock(SessionService.class);
        email = mock(EmailService.class);
        when(repo.save(any())).thenAnswer(i -> { stored.set(i.getArgument(0)); return i.getArgument(0); });
        when(repo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        service = new AdminTeamService(repo, NoOpPasswordEncoder.getInstance(), sessions, email,
                new EmailTemplates(new FrontendProperties("https://admin", "p", "c")), Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "exposeTokens", true);
    }

    private AdminUser member(AdminRole role, AccountStatus status) {
        AdminUser u = new AdminUser("نورة", "noura@vornx.sa", "hash", role);
        u.setStatus(status);
        ReflectionTestUtils.setField(u, "id", 9L);
        stored.set(u);
        return u;
    }

    @Test
    void anInviteCreatesAnInvitedAdminWithoutPasswordAndEmailsTheLink() {
        AdminTeamService.InviteResponse r = service.invite(new InviteRequest(" نورة ", "Noura@Vornx.sa", AdminRole.SUPPORT_AGENT), 1L);
        AdminUser u = stored.get();
        assertEquals(AccountStatus.INVITED, u.getStatus());
        assertEquals("noura@vornx.sa", u.getEmail());
        assertNull(u.getPasswordHash());
        assertEquals(SecureTokens.sha256(r.inviteToken()), u.getInviteTokenHash()); // only the hash is stored
        assertEquals(NOW.plus(AdminTeamService.INVITE_VALIDITY), u.getInviteExpiresAt());
        verify(email).queue(eq("noura@vornx.sa"), eq(EmailType.ADMIN_INVITE), any());
    }

    @Test
    void anEmailAlreadyOnTheTeamCantBeInvitedAgain() {
        when(repo.existsByEmail("noura@vornx.sa")).thenReturn(true);
        assertEquals("EMAIL_TAKEN", assertThrows(BusinessException.class,
                () -> service.invite(new InviteRequest("نورة", "noura@vornx.sa", AdminRole.AUDITOR), 1L)).getCode());
        verify(repo, never()).save(any());
    }

    @Test
    void acceptingActivatesOnceAndExpiredLinksAreRefused() {
        String token = service.invite(new InviteRequest("نورة", "noura@vornx.sa", AdminRole.AUDITOR), 1L).inviteToken();
        when(repo.findByInviteTokenHash(SecureTokens.sha256(token))).thenAnswer(i -> Optional.of(stored.get()));

        AdminUser u = service.acceptInvite(token, "a-long-password");
        assertEquals(AccountStatus.ACTIVE, u.getStatus());
        assertEquals("a-long-password", u.getPasswordHash());
        assertNull(u.getInviteTokenHash());
        assertEquals("INVITE_INVALID", assertThrows(BusinessException.class,
                () -> service.acceptInvite(token, "another-password")).getCode());

        AdminUser late = AdminUser.invite("x", "x@vornx.sa", AdminRole.AUDITOR, "h", NOW.minusSeconds(1));
        when(repo.findByInviteTokenHash(SecureTokens.sha256("old"))).thenReturn(Optional.of(late));
        assertEquals("INVITE_EXPIRED", assertThrows(BusinessException.class, () -> service.acceptInvite("old", "pw")).getCode());
    }

    @Test
    void nobodyChangesThemselves() {
        member(AdminRole.SUPER_ADMIN, AccountStatus.ACTIVE);
        assertEquals("CANNOT_CHANGE_SELF", assertThrows(BusinessException.class,
                () -> service.update(9L, 9L, new UpdateRequest(AdminRole.AUDITOR, null))).getCode());
    }

    @Test
    void theLastActiveSuperAdminCantBeDemotedOrSuspended() {
        member(AdminRole.SUPER_ADMIN, AccountStatus.ACTIVE);
        when(repo.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AccountStatus.ACTIVE)).thenReturn(1L);
        assertEquals("LAST_SUPER_ADMIN", assertThrows(BusinessException.class,
                () -> service.update(9L, 1L, new UpdateRequest(AdminRole.AUDITOR, null))).getCode());
        assertEquals("LAST_SUPER_ADMIN", assertThrows(BusinessException.class,
                () -> service.update(9L, 1L, new UpdateRequest(null, AccountStatus.SUSPENDED))).getCode());

        when(repo.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AccountStatus.ACTIVE)).thenReturn(2L);
        assertEquals(AdminRole.AUDITOR, service.update(9L, 1L, new UpdateRequest(AdminRole.AUDITOR, null)).role());
    }

    @Test
    void suspendingEndsTheirSessionsAndCancelsAPendingInvite() {
        AdminUser u = member(AdminRole.SUPPORT_AGENT, AccountStatus.INVITED);
        u.reissueInvite("h", NOW.plusSeconds(60));
        service.update(9L, 1L, new UpdateRequest(null, AccountStatus.SUSPENDED));
        assertEquals(AccountStatus.SUSPENDED, u.getStatus());
        assertNull(u.getInviteTokenHash());
        verify(sessions).revokeAll(UserType.ADMIN, 9L, null);
    }

    @Test
    void someoneWhoNeverSetAPasswordCantBeReactivatedButCanGetANewLink() {
        AdminUser u = member(AdminRole.SUPPORT_AGENT, AccountStatus.SUSPENDED);
        u.setPasswordHash(null);
        assertEquals("INVITE_PENDING", assertThrows(BusinessException.class,
                () -> service.update(9L, 1L, new UpdateRequest(null, AccountStatus.ACTIVE))).getCode());
        assertEquals("NOT_INVITED", assertThrows(BusinessException.class, () -> service.resendInvite(9L, 1L)).getCode());

        u.setStatus(AccountStatus.INVITED);
        String old = "h1";
        u.reissueInvite(old, NOW);
        assertNotNull(service.resendInvite(9L, 1L).inviteToken());
        assertEquals(NOW.plus(AdminTeamService.INVITE_VALIDITY), u.getInviteExpiresAt());
        verify(email).queue(eq("noura@vornx.sa"), eq(EmailType.ADMIN_INVITE), any());
    }
}
