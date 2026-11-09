package com.example.vornx.audit.service;

import com.example.vornx.audit.dto.ActivityLogResponse;
import com.example.vornx.audit.entity.ActivityLog;
import com.example.vornx.audit.repository.ActivityLogRepository;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.enums.ActorType;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final AdminUserService adminUserService;
    private final PartnerUserService partnerUserService;
    private final CustomerService customerService;

    /**
     * REQUIRES_NEW = its own small transaction. The audited action has already committed by now;
     * writing the log line must not depend on (or roll back) anything else.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ActorType actorType, Long actorId, Long partnerId, String action, String targetType,
                       Long targetId, String details, String ipAddress) {
        activityLogRepository.save(new ActivityLog(actorType, actorId, nameOf(actorType, actorId), partnerId, action,
                targetType, targetId, details, ipAddress));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> search(ActorType actorType, Long partnerId, String action,
                                                    String targetType, Long targetId, Instant from, Instant to,
                                                    Pageable pageable) {
        return PageResponse.from(activityLogRepository.search(actorType, partnerId, blankToNull(action),
                blankToNull(targetType), targetId, from, to, pageable).map(ActivityLogResponse::from));
    }

    /** The name is copied into the log, so it stays readable even if the person is renamed or removed later. */
    private String nameOf(ActorType type, Long id) {
        if (id == null) {
            return type == ActorType.SYSTEM ? "System" : "Unknown";
        }
        return switch (type) {
            case ADMIN -> adminUserService.getEntity(id).getFullName();
            case PARTNER_USER -> partnerUserService.getProfile(id).fullName();
            case CUSTOMER -> {
                Customer c = customerService.getEntity(id);
                yield c.getFirstName() + " " + c.getLastName();
            }
            case SYSTEM -> "Device (API key #" + id + ")";
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }
}
