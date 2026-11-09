package com.example.vornx.security.session;

import com.example.vornx.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * "My devices" for the 3 portals: list logged-in devices, log one out, log out everywhere.
 * Note: a revoked device can still use its current access token until it expires (max 15 min),
 * but it can never refresh again.
 */
public final class SessionControllers {

    private SessionControllers() {
    }

    @RequiredArgsConstructor
    abstract static class Base {

        protected final SessionService sessionService;

        @GetMapping
        public List<SessionResponse> list(@AuthenticationPrincipal Jwt jwt) {
            AuthUser me = AuthUser.from(jwt);
            return sessionService.list(me.type(), me.id(), me.sessionId());
        }

        @DeleteMapping("/{sessionId}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) {
            AuthUser me = AuthUser.from(jwt);
            sessionService.revokeOne(me.type(), me.id(), sessionId);
        }

        /** DELETE /sessions?keepCurrent=true -> "log out all OTHER devices" (default). false = everywhere. */
        @DeleteMapping
        public Map<String, Integer> revokeAll(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "true") boolean keepCurrent) {
            AuthUser me = AuthUser.from(jwt);
            return Map.of("revoked", sessionService.revokeAll(me.type(), me.id(), keepCurrent ? me.sessionId() : null));
        }
    }

    @Tag(name = "Customer - Devices", description = "My logged-in devices (\"الأجهزة\")")
    @RestController
    @RequestMapping("/api/customer/sessions")
    public static class Customer extends Base {
        public Customer(SessionService s) {
            super(s);
        }
    }

    @Tag(name = "Partner - Devices", description = "My logged-in devices")
    @RestController
    @RequestMapping("/api/partner/sessions")
    public static class Partner extends Base {
        public Partner(SessionService s) {
            super(s);
        }
    }

    @Tag(name = "Admin - Devices", description = "My logged-in devices")
    @RestController
    @RequestMapping("/api/admin/sessions")
    public static class Admin extends Base {
        public Admin(SessionService s) {
            super(s);
        }
    }
}
