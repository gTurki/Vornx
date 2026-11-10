package com.example.vornx.inquiry;

import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The website's contact form. Public, so it defends itself:
 *  - a honeypot field ("website") that people never see but simple bots fill in,
 *  - at most 5 messages per IP per hour and 3 per email per day,
 *  - strict sizes, and the text is only ever shown as text (React escapes it).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InquiryService {

    private static final int PER_IP_PER_HOUR = 5;
    private static final int PER_EMAIL_PER_DAY = 3;

    private final InquiryRepository repository;
    private final NotificationService notificationService;
    private final AdminUserService adminUserService;
    private final Clock clock;

    // ---------- DTOs ----------

    /** Field names match the website's form. */
    public record InquiryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 160) String organization,
            @Size(max = 120) String role,
            @NotNull Inquiry.Interest interest,
            @NotBlank @Size(max = 2000) String message,
            @Pattern(regexp = "^(ar|en|AR|EN)?$") String language,
            @AssertTrue(message = "Consent is required") boolean consent,
            String website // honeypot: must stay empty
    ) {
    }

    public record InquiryResponse(Long id, String name, String email, String organization, String jobTitle,
                                  Inquiry.Interest interest, String message, String language, Inquiry.Status status,
                                  String handledBy, Instant handledAt, Instant createdAt) {
        static InquiryResponse from(Inquiry i) {
            return new InquiryResponse(i.getId(), i.getName(), i.getEmail(), i.getOrganization(), i.getJobTitle(),
                    i.getInterest(), i.getMessage(), i.getLanguage(), i.getStatus(),
                    i.getHandledBy() != null ? i.getHandledBy().getFullName() : null, i.getHandledAt(), i.getCreatedAt());
        }
    }

    // ---------- public ----------

    public void submit(InquiryRequest req, String ip, String userAgent) {
        if (req.website() != null && !req.website().isBlank()) {
            log.info("Inquiry honeypot triggered from {}", ip);
            return; // pretend it worked: don't teach the bot anything
        }
        Instant now = clock.instant();
        String email = req.email().trim().toLowerCase();
        if (ip != null && repository.countByIpAddressAndCreatedAtAfter(ip, now.minus(Duration.ofHours(1))) >= PER_IP_PER_HOUR
                || repository.countByEmailIgnoreCaseAndCreatedAtAfter(email, now.minus(Duration.ofDays(1))) >= PER_EMAIL_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages. Please try again later.");
        }
        String role = req.role() != null && !req.role().isBlank() ? req.role().trim() : null;
        String language = "ar".equalsIgnoreCase(req.language()) ? "AR" : "EN";
        Inquiry saved = repository.save(new Inquiry(req.name().trim(), email, req.organization().trim(), role,
                req.interest(), req.message().trim(), language, ip, userAgent));
        notificationService.notifyAdmins(NotificationType.NEW_INQUIRY,
                Map.of("name", saved.getName(), "organization", saved.getOrganization(), "interest", saved.getInterest()),
                NotificationService.Ref.of("INQUIRY", saved.getId()));
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<InquiryResponse> list(Inquiry.Status status, Pageable pageable) {
        return PageResponse.from(repository.search(status, pageable).map(InquiryResponse::from));
    }

    @Transactional(readOnly = true)
    public long countNew() {
        return repository.countByStatus(Inquiry.Status.NEW);
    }

    public InquiryResponse setStatus(Long id, Inquiry.Status status, Long adminId) {
        Inquiry i = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inquiry", id));
        if (status == Inquiry.Status.HANDLED) {
            i.markHandled(adminUserService.getEntity(adminId), clock.instant());
        } else {
            i.reopen();
        }
        return InquiryResponse.from(i);
    }
}
