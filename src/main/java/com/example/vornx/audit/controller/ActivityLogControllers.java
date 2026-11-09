package com.example.vornx.audit.controller;

import com.example.vornx.audit.dto.ActivityLogResponse;
import com.example.vornx.audit.service.ActivityLogService;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.enums.ActorType;
import com.example.vornx.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** "سجل النشاط" for the admin dashboard and the partner portal. */
public final class ActivityLogControllers {

    private ActivityLogControllers() {
    }

    @Tag(name = "Admin - Activity log", description = "Full audit trail: who did what, when, on which record")
    @RestController
    @RequiredArgsConstructor
    public static class Admin {

        private final ActivityLogService activityLogService;

        /** GET /api/admin/activity-log?partnerId=2&action=PERMIT_REVOKED&from=2026-11-01T00:00:00Z */
        @GetMapping("/api/admin/activity-log")
        public PageResponse<ActivityLogResponse> search(@RequestParam(required = false) ActorType actorType,
                                                        @RequestParam(required = false) Long partnerId,
                                                        @RequestParam(required = false) String action,
                                                        @RequestParam(required = false) String targetType,
                                                        @RequestParam(required = false) Long targetId,
                                                        @RequestParam(required = false) Instant from,
                                                        @RequestParam(required = false) Instant to,
                                                        @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
                                                        Pageable pageable) {
            return activityLogService.search(actorType, partnerId, action, targetType, targetId, from, to, pageable);
        }
    }

    @Tag(name = "Partner - Activity log", description = "Everything your team did (\"كل إجراء يُسجَّل\")")
    @RestController
    @RequiredArgsConstructor
    public static class Partner {

        private final ActivityLogService activityLogService;

        /** Only this partner's staff actions - never other partners', never VORNX internals. */
        @GetMapping("/api/partner/activity-log")
        public PageResponse<ActivityLogResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(required = false) String action,
                                                      @RequestParam(required = false) Instant from,
                                                      @RequestParam(required = false) Instant to,
                                                      @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
                                                      Pageable pageable) {
            return activityLogService.search(ActorType.PARTNER_USER, AuthUser.from(jwt).partnerId(), action, null, null,
                    from, to, pageable);
        }
    }
}
