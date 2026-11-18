package com.example.vornx.partner.application;

import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;
import com.example.vornx.partner.dto.InvitePartnerUserRequest;
import com.example.vornx.partner.dto.PartnerRequest;
import com.example.vornx.partner.dto.PartnerUserInviteResponse;
import com.example.vornx.partner.enums.PartnerRole;
import com.example.vornx.partner.enums.Sector;
import com.example.vornx.partner.repository.PartnerRepository;
import com.example.vornx.partner.service.PartnerService;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.service.AdminUserService;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * "انضم كشريك": companies apply on the website, the VORNX team decides.
 *
 * Why not let partners sign up and start straight away: a partner controls doors and sees its customers' entries,
 * so we must know who it is first (commercial registration, contract). Approving creates the partner (ONBOARDING:
 * it can set up its portal; it becomes ACTIVE with its contract) and emails the contact an invite to set a password.
 *
 * The public form defends itself like the contact form: honeypot field, 3 applications per IP per hour and per email
 * per day, strict sizes, and one open application per CR number.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PartnerApplicationService {

    static final int PER_IP_PER_HOUR = 3;
    static final int PER_EMAIL_PER_DAY = 3;

    private final PartnerApplicationRepository repository;
    private final PartnerRepository partners;
    private final PartnerService partnerService;
    private final PartnerUserService partnerUsers;
    private final AdminUserService admins;
    private final NotificationService notifications;
    private final EmailService emailService;
    private final EmailTemplates templates;
    private final Clock clock;

    // ---------- DTOs ----------

    /** The website's "انضم كشريك" form. */
    public record ApplicationRequest(
            @NotBlank @Size(max = 150) String companyName,
            @NotBlank @Pattern(regexp = "^\\d{10}$", message = "CR number must be 10 digits") String crNumber,
            @NotNull Sector sector,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 120) String contactName,
            @Size(max = 120) String jobTitle,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = "^05\\d{8}$", message = "Mobile format: 05XXXXXXXX") String phone,
            @Size(max = 2000) String message,
            @Pattern(regexp = "^(ar|en|AR|EN)?$") String language,
            @AssertTrue(message = "Consent is required") boolean consent,
            String website // honeypot: must stay empty
    ) {
    }

    public record ApplicationResponse(Long id, String companyName, String crNumber, Sector sector, String city,
                                      String contactName, String jobTitle, String email, String phone, String message,
                                      String language, PartnerApplication.Status status, String rejectReason,
                                      Long partnerId, String reviewedBy, Instant reviewedAt, Instant createdAt) {
        static ApplicationResponse from(PartnerApplication a) {
            return new ApplicationResponse(a.getId(), a.getCompanyName(), a.getCrNumber(), a.getSector(), a.getCity(),
                    a.getContactName(), a.getJobTitle(), a.getContactEmail(), a.getContactPhone(), a.getMessage(),
                    a.getLanguage(), a.getStatus(), a.getRejectReason(), a.getPartnerId(),
                    a.getReviewedBy() != null ? a.getReviewedBy().getFullName() : null, a.getReviewedAt(), a.getCreatedAt());
        }
    }

    /** inviteToken: development only (vornx.dev.expose-tokens), the invite travels by email. */
    public record ApprovalResponse(ApplicationResponse application, Long partnerId, String inviteToken) {
    }

    // ---------- public ----------

    public void submit(ApplicationRequest req, String ip, String userAgent) {
        if (StringUtils.hasText(req.website())) {
            log.info("Partner application honeypot triggered from {}", ip);
            return; // pretend it worked: don't teach the bot anything
        }
        Instant now = clock.instant();
        String email = req.email().trim().toLowerCase();
        if (ip != null && repository.countByIpAddressAndCreatedAtAfter(ip, now.minus(Duration.ofHours(1))) >= PER_IP_PER_HOUR
                || repository.countByContactEmailIgnoreCaseAndCreatedAtAfter(email, now.minus(Duration.ofDays(1))) >= PER_EMAIL_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many applications. Please try again later.");
        }
        String cr = req.crNumber().trim();
        if (partners.existsByCrNumber(cr)) {
            throw new BusinessException("ALREADY_PARTNER", "This company is already a VORNX partner");
        }
        if (repository.existsByCrNumberAndStatus(cr, PartnerApplication.Status.PENDING)) {
            throw new BusinessException("APPLICATION_PENDING", "An application for this company is already under review");
        }
        String language = "en".equalsIgnoreCase(req.language()) ? "EN" : "AR";
        PartnerApplication saved = repository.save(new PartnerApplication(req.companyName().trim(), cr, req.sector(),
                req.city().trim(), req.contactName().trim(), blankToNull(req.jobTitle()), email, req.phone().trim(),
                blankToNull(req.message()), language, ip, userAgent));
        emailService.queue(email, EmailType.PARTNER_APPLICATION,
                templates.partnerApplicationReceived(saved.getContactName(), saved.getCompanyName()));
        notifications.notifyAdmins(NotificationType.NEW_PARTNER_APPLICATION,
                Map.of("company", saved.getCompanyName(), "city", saved.getCity()),
                NotificationService.Ref.of("PARTNER_APPLICATION", saved.getId()));
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> list(PartnerApplication.Status status, Pageable pageable) {
        return PageResponse.from(repository.search(status, pageable).map(ApplicationResponse::from));
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countByStatus(PartnerApplication.Status.PENDING);
    }

    /**
     * Approve: creates the partner (ONBOARDING) with the reviewer (or the chosen admin) as account manager, and
     * invites the contact as its PARTNER_ADMIN (email with a link to set a password). All or nothing.
     */
    public ApprovalResponse approve(Long id, Long adminId, Long accountManagerId) {
        PartnerApplication a = pending(id);
        AdminUser reviewer = admins.getEntity(adminId);
        Long managerId = accountManagerId != null ? accountManagerId : adminId;
        Long partnerId = partnerService.create(new PartnerRequest(a.getCompanyName(), a.getSector(), a.getCrNumber(),
                a.getContactPhone(), a.getCity(), "SA", a.getMessage(), managerId)).id();
        PartnerUserInviteResponse invite = partnerUsers.invite(partnerId,
                new InvitePartnerUserRequest(a.getContactName(), a.getContactEmail(), PartnerRole.PARTNER_ADMIN));
        a.approve(partnerId, reviewer, clock.instant());
        return new ApprovalResponse(ApplicationResponse.from(a), partnerId, invite.inviteToken());
    }

    /** Reject with a reason: the applicant gets it by email, politely. */
    public ApplicationResponse reject(Long id, Long adminId, String reason) {
        PartnerApplication a = pending(id);
        a.reject(reason.trim(), admins.getEntity(adminId), clock.instant());
        emailService.queue(a.getContactEmail(), EmailType.PARTNER_APPLICATION,
                templates.partnerApplicationRejected(a.getContactName(), a.getCompanyName(), a.getRejectReason()));
        return ApplicationResponse.from(a);
    }

    private PartnerApplication pending(Long id) {
        PartnerApplication a = repository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Application", id));
        if (!a.isPending()) {
            throw new BusinessException("APPLICATION_DECIDED", "This application has already been decided");
        }
        return a;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
