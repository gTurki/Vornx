package com.example.vornx.partner.application;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.email.FrontendProperties;
import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;
import com.example.vornx.partner.application.PartnerApplicationService.ApplicationRequest;
import com.example.vornx.partner.dto.InvitePartnerUserRequest;
import com.example.vornx.partner.dto.PartnerRequest;
import com.example.vornx.partner.dto.PartnerResponse;
import com.example.vornx.partner.dto.PartnerUserInviteResponse;
import com.example.vornx.partner.enums.PartnerRole;
import com.example.vornx.partner.enums.Sector;
import com.example.vornx.partner.repository.PartnerRepository;
import com.example.vornx.partner.service.PartnerService;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "انضم كشريك": the public form's protections, and approve / reject. Database = Mockito fakes. */
class PartnerApplicationRulesTest {

    private PartnerApplicationRepository repo;
    private PartnerRepository partners;
    private PartnerService partnerService;
    private PartnerUserService partnerUsers;
    private EmailService email;
    private NotificationService notifications;
    private PartnerApplicationService service;
    private final AtomicReference<PartnerApplication> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        repo = mock(PartnerApplicationRepository.class);
        partners = mock(PartnerRepository.class);
        partnerService = mock(PartnerService.class);
        partnerUsers = mock(PartnerUserService.class);
        email = mock(EmailService.class);
        notifications = mock(NotificationService.class);
        AdminUserService admins = mock(AdminUserService.class);
        when(admins.getEntity(anyLong())).thenReturn(mock(AdminUser.class));
        when(repo.save(any())).thenAnswer(i -> { stored.set(i.getArgument(0)); return i.getArgument(0); });
        when(repo.lockById(anyLong())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        service = new PartnerApplicationService(repo, partners, partnerService, partnerUsers, admins, notifications, email,
                new EmailTemplates(new FrontendProperties("a", "p", "c")), Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
    }

    private static ApplicationRequest request(String honeypot) {
        return new ApplicationRequest("فندق الاختبار", "7012340000", Sector.HOTEL, "الرياض", "سارة", "مديرة العمليات",
                "Sara@Example.com", "0551234567", "نريد المفتاح الرقمي", "ar", true, honeypot);
    }

    @Test
    void anApplicationIsSavedConfirmedByEmailAndTheTeamIsNotified() {
        service.submit(request(null), "1.2.3.4", "ua");
        PartnerApplication a = stored.get();
        assertEquals(PartnerApplication.Status.PENDING, a.getStatus());
        assertEquals("sara@example.com", a.getContactEmail());
        assertEquals("AR", a.getLanguage());
        verify(email).queue(eq("sara@example.com"), eq(EmailType.PARTNER_APPLICATION), any());
        verify(notifications).notifyAdmins(eq(NotificationType.NEW_PARTNER_APPLICATION), any(), any());
    }

    @Test
    void theHoneypotSilentlyDropsBots() {
        service.submit(request("http://spam"), "1.2.3.4", "ua");
        verify(repo, never()).save(any());
        verify(email, never()).queue(anyString(), any(), any());
    }

    @Test
    void tooManyApplicationsAreRefused() {
        when(repo.countByIpAddressAndCreatedAtAfter(eq("1.2.3.4"), any())).thenReturn(3L);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.submit(request(null), "1.2.3.4", "ua"));
        assertEquals(429, e.getStatusCode().value());
    }

    @Test
    void anExistingPartnerOrAnOpenApplicationCantApplyAgain() {
        when(partners.existsByCrNumber("7012340000")).thenReturn(true);
        assertEquals("ALREADY_PARTNER", assertThrows(BusinessException.class, () -> service.submit(request(null), "1.1.1.1", "ua")).getCode());

        when(partners.existsByCrNumber("7012340000")).thenReturn(false);
        when(repo.existsByCrNumberAndStatus("7012340000", PartnerApplication.Status.PENDING)).thenReturn(true);
        assertEquals("APPLICATION_PENDING", assertThrows(BusinessException.class, () -> service.submit(request(null), "1.1.1.1", "ua")).getCode());
    }

    @Test
    void approvingCreatesThePartnerAndInvitesTheContactOnce() {
        service.submit(request(null), "1.2.3.4", "ua");
        PartnerResponse created = mock(PartnerResponse.class);
        when(created.id()).thenReturn(42L);
        when(partnerService.create(any())).thenReturn(created);
        when(partnerUsers.invite(eq(42L), any())).thenReturn(new PartnerUserInviteResponse(null, null, null));

        PartnerApplicationService.ApprovalResponse r = service.approve(5L, 7L, null);

        ArgumentCaptor<PartnerRequest> partner = ArgumentCaptor.forClass(PartnerRequest.class);
        verify(partnerService).create(partner.capture());
        assertEquals("7012340000", partner.getValue().crNumber());
        assertEquals(7L, partner.getValue().accountManagerId()); // the reviewer by default
        ArgumentCaptor<InvitePartnerUserRequest> invite = ArgumentCaptor.forClass(InvitePartnerUserRequest.class);
        verify(partnerUsers).invite(eq(42L), invite.capture());
        assertEquals("sara@example.com", invite.getValue().email());
        assertEquals(PartnerRole.PARTNER_ADMIN, invite.getValue().role());
        assertEquals(42L, r.partnerId());
        assertEquals(PartnerApplication.Status.APPROVED, stored.get().getStatus());

        assertEquals("APPLICATION_DECIDED", assertThrows(BusinessException.class, () -> service.approve(5L, 7L, null)).getCode());
        assertEquals("APPLICATION_DECIDED", assertThrows(BusinessException.class, () -> service.reject(5L, 7L, "x")).getCode());
    }

    @Test
    void rejectingEmailsTheReason() {
        service.submit(request(null), "1.2.3.4", "ua");
        service.reject(5L, 7L, "  السجل التجاري غير ساري  ");
        assertEquals(PartnerApplication.Status.REJECTED, stored.get().getStatus());
        assertEquals("السجل التجاري غير ساري", stored.get().getRejectReason());
        verify(email, org.mockito.Mockito.times(2)).queue(eq("sara@example.com"), eq(EmailType.PARTNER_APPLICATION), any());
        verify(partnerService, never()).create(any());
    }
}
