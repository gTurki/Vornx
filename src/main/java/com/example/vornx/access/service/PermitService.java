package com.example.vornx.access.service;
import java.util.Map;

import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;

import com.example.vornx.access.dto.PermitExtendRequest;
import com.example.vornx.access.dto.PermitIssueRequest;
import com.example.vornx.access.dto.PermitResponse;
import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.entity.Permit;
import com.example.vornx.access.entity.Site;
import com.example.vornx.access.enums.AccessLevel;
import com.example.vornx.access.enums.PermitStatus;
import com.example.vornx.access.enums.PermitType;
import com.example.vornx.access.repository.PermitRepository;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermitService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PermitRepository permitRepository;
    private final AccessPointService accessPointService;
    private final SiteService siteService;
    private final CustomerService customerService;
    private final Clock clock;
    private final NotificationService notificationService;

    // ---------- Partner side ----------

    @Transactional
    public PermitResponse issue(Long partnerId, PermitIssueRequest req) {
        Site site = siteService.getOwnedSite(partnerId, req.siteId());
        Customer customer = customerService.getActiveByVornxId(req.customerVornxId());
        Instant now = Instant.now(clock);

        Instant validFrom = req.validFrom() != null ? req.validFrom() : now;
        if (!req.validUntil().isAfter(validFrom)) {
            throw new BusinessException("INVALID_PERIOD", "validUntil must be after validFrom");
        }
        if (!req.validUntil().isAfter(now)) {
            throw new BusinessException("INVALID_PERIOD", "validUntil must be in the future");
        }
        if ((req.allowedFrom() == null) != (req.allowedTo() == null)) {
            throw new BusinessException("HOURS_INCOMPLETE", "Set both allowedFrom and allowedTo, or neither");
        }

        Permit permit = Permit.issue(generatePermitNumber(), generateVerificationCode(), customer, site.getPartner(),
                site, req.type(), req.level() != null ? req.level() : AccessLevel.STANDARD,
                validFrom, req.validUntil());
        if (req.allowedDays() != null) {
            permit.getAllowedDays().addAll(req.allowedDays());
        }
        permit.setAllowedFrom(req.allowedFrom());
        permit.setAllowedTo(req.allowedTo());
        permit.setConditions(StringUtils.hasText(req.conditions()) ? req.conditions().trim() : null);
        permit.getAccessPoints().addAll(accessPointService.findInSite(site, req.accessPointIds()));

        permitRepository.save(permit);
        notificationService.notifyCustomer(customer.getId(), NotificationType.PERMIT_ISSUED,
                Map.of("partner", site.getPartner().getName(), "site", site.getName()),
                NotificationService.Ref.of("PERMIT", permit.getId()));
        return PermitResponse.from(permit, now);
    }

    public PageResponse<PermitResponse> listForPartner(Long partnerId, Long siteId, Pageable pageable) {
        Instant now = Instant.now(clock);
        Page<Permit> page = siteId != null
                ? permitRepository.findByPartner_IdAndSite_Id(partnerId, siteId, pageable)
                : permitRepository.findByPartner_Id(partnerId, pageable);
        return PageResponse.from(page.map(p -> PermitResponse.from(p, now)));
    }

    public PermitResponse getForPartner(Long partnerId, Long permitId) {
        return PermitResponse.from(getOwnedByPartner(partnerId, permitId), Instant.now(clock));
    }

    /** "إلغاء المفتاح" - the permit stops working immediately. */
    @Transactional
    public PermitResponse revoke(Long partnerId, Long permitId) {
        Permit permit = getOwnedByPartner(partnerId, permitId);
        if (permit.getStatus() == PermitStatus.REVOKED) {
            throw new BusinessException("ALREADY_REVOKED", "This permit is already revoked");
        }
        permit.setStatus(PermitStatus.REVOKED);
        notificationService.notifyCustomer(permit.getCustomer().getId(), NotificationType.PERMIT_REVOKED,
                Map.of("partner", permit.getPartner().getName(), "site", permit.getSite().getName()),
                NotificationService.Ref.of("PERMIT", permit.getId()));
        return PermitResponse.from(permit, Instant.now(clock));
    }

    /** "تمديد" - move the end date. */
    @Transactional
    public PermitResponse extend(Long partnerId, Long permitId, PermitExtendRequest req) {
        Permit permit = getOwnedByPartner(partnerId, permitId);
        Instant now = Instant.now(clock);
        if (permit.getStatus() == PermitStatus.REVOKED) {
            throw new BusinessException("PERMIT_REVOKED", "A revoked permit cannot be extended");
        }
        if (!req.validUntil().isAfter(permit.getValidFrom()) || !req.validUntil().isAfter(now)) {
            throw new BusinessException("INVALID_PERIOD", "The new end must be in the future and after validFrom");
        }
        permit.setValidUntil(req.validUntil());
        return PermitResponse.from(permit, now);
    }

    // ---------- For the subscription module ----------

    /** Permits created by the system from a template (an approved subscription, a confirmed event ticket...). */
    @Transactional
    public Permit issueFromTemplate(Customer customer, Site site, Collection<AccessPoint> accessPoints,
                                      PermitType type, Instant validFrom, Instant validUntil,
                                      Set<DayOfWeek> allowedDays, LocalTime allowedFrom, LocalTime allowedTo,
                                      String conditions) {
        Permit permit = Permit.issue(generatePermitNumber(), generateVerificationCode(), customer, site.getPartner(),
                site, type, AccessLevel.STANDARD, validFrom, validUntil);
        permit.getAllowedDays().addAll(allowedDays);
        permit.setAllowedFrom(allowedFrom);
        permit.setAllowedTo(allowedTo);
        permit.setConditions(conditions);
        permit.getAccessPoints().addAll(accessPoints);
        return permitRepository.save(permit);
    }

    // ---------- Customer side ----------

    public List<PermitResponse> listForCustomer(Long customerId) {
        Instant now = Instant.now(clock);
        return permitRepository.findByCustomer_IdOrderByValidUntilDesc(customerId).stream()
                .map(p -> PermitResponse.from(p, now))
                .toList();
    }

    public PermitResponse getForCustomer(Long customerId, Long permitId) {
        Permit permit = permitRepository.findByIdAndCustomer_Id(permitId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Permit", permitId));
        return PermitResponse.from(permit, Instant.now(clock));
    }

    // ---------- helpers ----------

    private Permit getOwnedByPartner(Long partnerId, Long permitId) {
        return permitRepository.findByIdAndPartner_Id(permitId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Permit", permitId));
    }

    private String generatePermitNumber() {
        String number;
        do {
            number = "PRM-" + String.format("%08d", RANDOM.nextInt(100_000_000));
        } while (permitRepository.existsByPermitNumber(number));
        return number;
    }

    private static String generateVerificationCode() {
        return String.format("%08d", RANDOM.nextInt(100_000_000));
    }
}
