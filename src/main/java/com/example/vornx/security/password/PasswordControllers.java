package com.example.vornx.security.password;

import com.example.vornx.audit.Audited;
import com.example.vornx.security.AuthUser;
import com.example.vornx.security.UserType;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

public final class PasswordControllers {

    private PasswordControllers() {
    }

    /** Public: "نسيت كلمة المرور؟" for the 3 portals, and the reset itself. */
    @Tag(name = "Authentication")
    @SecurityRequirements
    @RestController
    @RequiredArgsConstructor
    public static class Public {

        private final PasswordService passwordService;

        /**
         * POST /api/public/auth/customer/forgot-password  (or admin / partner)
         * Always 202 Accepted - the response never tells whether the email exists.
         */
        @PostMapping("/api/public/auth/{portal}/forgot-password")
        @ResponseStatus(HttpStatus.ACCEPTED)
        public void forgot(@PathVariable String portal, @Valid @RequestBody PasswordDtos.ForgotPasswordRequest req) {
            passwordService.forgot(typeOf(portal), req.email());
        }

        /** The link in the email opens the frontend, which sends the token here with the new password. */
        @PostMapping("/api/public/auth/reset-password")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void reset(@Valid @RequestBody PasswordDtos.ResetPasswordRequest req) {
            passwordService.reset(req.token(), req.password(), req.confirmPassword());
        }

        private static UserType typeOf(String portal) {
            return switch (portal) {
                case "admin" -> UserType.ADMIN;
                case "partner" -> UserType.PARTNER_USER;
                case "customer" -> UserType.CUSTOMER;
                default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            };
        }
    }

    /** Logged in: change my password (the 3 portals share this code). */
    @RequiredArgsConstructor
    abstract static class Change {

        protected final PasswordService passwordService;

        @PostMapping("/me/password")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Audited(action = "PASSWORD_CHANGED", target = "ACCOUNT", targetId = "#jwt.getSubject()")
        public void change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordDtos.ChangePasswordRequest req) {
            passwordService.change(AuthUser.from(jwt), req.currentPassword(), req.newPassword(), req.confirmPassword());
        }
    }

    @Tag(name = "Customer - Profile")
    @RestController
    @RequestMapping("/api/customer")
    public static class Customer extends Change {
        public Customer(PasswordService s) {
            super(s);
        }
    }

    @Tag(name = "Partner - Profile")
    @RestController
    @RequestMapping("/api/partner")
    public static class Partner extends Change {
        public Partner(PasswordService s) {
            super(s);
        }
    }

    @Tag(name = "Admin - Profile")
    @RestController
    @RequestMapping("/api/admin")
    public static class Admin extends Change {
        public Admin(PasswordService s) {
            super(s);
        }
    }
}
