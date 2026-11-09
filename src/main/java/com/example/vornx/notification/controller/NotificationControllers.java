package com.example.vornx.notification.controller;

import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.notification.dto.NotificationResponse;
import com.example.vornx.notification.enums.RecipientType;
import com.example.vornx.notification.service.NotificationService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The bell icon, for the 3 portals. Same 4 endpoints each; only "whose inbox" differs.
 * The base class holds the shared code; each small subclass says whose inbox it is.
 */
public final class NotificationControllers {

    private NotificationControllers() {
    }

    @RequiredArgsConstructor
    abstract static class Base {

        protected final NotificationService notificationService;

        protected abstract RecipientType type();

        /** Which inbox, taken from the token. */
        protected abstract Long recipientId(AuthUser me);

        @GetMapping
        public PageResponse<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                       @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                       Pageable pageable) {
            return notificationService.list(type(), recipientId(AuthUser.from(jwt)), unreadOnly, pageable);
        }

        /** The red number on the bell. */
        @GetMapping("/unread-count")
        public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("unread", notificationService.unreadCount(type(), recipientId(AuthUser.from(jwt))));
        }

        @PostMapping("/{notificationId}/read")
        public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long notificationId) {
            return notificationService.markRead(type(), recipientId(AuthUser.from(jwt)), notificationId);
        }

        @PostMapping("/read-all")
        public Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("marked", notificationService.markAllRead(type(), recipientId(AuthUser.from(jwt))));
        }
    }

    @Tag(name = "Customer - Notifications", description = "My notifications")
    @RestController
    @RequestMapping("/api/customer/notifications")
    public static class Customer extends Base {
        public Customer(NotificationService s) {
            super(s);
        }

        @Override
        protected RecipientType type() {
            return RecipientType.CUSTOMER;
        }

        @Override
        protected Long recipientId(AuthUser me) {
            return me.id();
        }
    }

    @Tag(name = "Partner - Notifications", description = "The team's shared notifications")
    @RestController
    @RequestMapping("/api/partner/notifications")
    public static class Partner extends Base {
        public Partner(NotificationService s) {
            super(s);
        }

        @Override
        protected RecipientType type() {
            return RecipientType.PARTNER;
        }

        @Override
        protected Long recipientId(AuthUser me) {
            return me.partnerId();
        }
    }

    @Tag(name = "Admin - Notifications", description = "The VORNX team inbox")
    @RestController
    @RequestMapping("/api/admin/notifications")
    public static class Admin extends Base {
        public Admin(NotificationService s) {
            super(s);
        }

        @Override
        protected RecipientType type() {
            return RecipientType.ADMINS;
        }

        @Override
        protected Long recipientId(AuthUser me) {
            return null;
        }
    }
}
