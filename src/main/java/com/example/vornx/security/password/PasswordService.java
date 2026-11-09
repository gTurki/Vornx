package com.example.vornx.security.password;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.partner.entity.PartnerUser;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.service.AdminUserService;
import com.example.vornx.security.AuthUser;
import com.example.vornx.security.UserType;
import com.example.vornx.security.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Forgot password -> email with a link -> reset; and "change password" while logged in.
 *
 * Security rules:
 *  - "forgot" ALWAYS answers the same (202), whether the email exists or not: no account discovery.
 *  - the link is single-use, valid 30 minutes, and only its hash is stored.
 *  - max one reset email per 2 minutes per account (stops someone flooding a victim's inbox).
 *  - after a reset, ALL sessions are logged out; after a change, all OTHER devices are.
 *  - a "your password was changed" email is always sent, so a victim notices an attacker.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordService {

    private static final String PREFIX = "pr_";
    private static final Duration RESET_VALIDITY = Duration.ofMinutes(30);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(2);

    private final PasswordResetTokenRepository tokenRepository;
    private final AdminUserService adminUserService;
    private final PartnerUserService partnerUserService;
    private final CustomerService customerService;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final EmailService emailService;
    private final EmailTemplates templates;
    private final Clock clock;

    /** The few things we need from any kind of user. */
    private record Account(UserType type, Long id, String email, String name, String passwordHash,
                           AccountStatus status, Consumer<String> setPasswordHash) {
    }

    // =============== Forgot / reset ===============

    public void forgot(UserType type, String email) {
        Optional<Account> found = findByEmail(type, email.trim().toLowerCase())
                .filter(a -> a.status() == AccountStatus.ACTIVE);
        if (found.isEmpty()) {
            log.info("Password reset requested for unknown/inactive {} account", type);
            return; // same answer as success, on purpose
        }
        Account account = found.get();
        Instant now = Instant.now(clock);
        if (tokenRepository.existsByUserTypeAndUserIdAndCreatedAtAfter(type, account.id(), now.minus(RESEND_COOLDOWN))) {
            return; // a link was sent less than 2 minutes ago
        }
        String raw = PREFIX + SecureTokens.generate();
        tokenRepository.save(new PasswordResetToken(type, account.id(), SecureTokens.sha256(raw), now.plus(RESET_VALIDITY)));
        emailService.queue(account.email(), EmailType.PASSWORD_RESET,
                templates.passwordReset(account.name(), type, raw, (int) RESET_VALIDITY.toMinutes()));
    }

    public void reset(String rawToken, String password, String confirm) {
        requireMatch(password, confirm);
        Instant now = Instant.now(clock);
        PasswordResetToken token = (rawToken != null && rawToken.startsWith(PREFIX)
                ? tokenRepository.lockByHash(SecureTokens.sha256(rawToken)) : Optional.<PasswordResetToken>empty())
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BusinessException("RESET_LINK_INVALID",
                        "This reset link is invalid, expired or already used. Ask for a new one."));
        Account account = findById(token.getUserType(), token.getUserId());
        if (account.status() != AccountStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_INACTIVE", "This account is not active");
        }

        account.setPasswordHash().accept(passwordEncoder.encode(password));
        tokenRepository.invalidateAll(account.type(), account.id(), now); // this link and any older ones
        sessionService.revokeAll(account.type(), account.id(), null);    // log out everywhere
        emailService.queue(account.email(), EmailType.PASSWORD_CHANGED, templates.passwordChanged(account.name(), account.type()));
    }

    // =============== Change (logged in) ===============

    public void change(AuthUser me, String current, String newPassword, String confirm) {
        requireMatch(newPassword, confirm);
        Account account = findById(me.type(), me.id());
        if (account.passwordHash() == null || !passwordEncoder.matches(current, account.passwordHash())) {
            throw new BusinessException("WRONG_PASSWORD", "Your current password is not correct");
        }
        if (passwordEncoder.matches(newPassword, account.passwordHash())) {
            throw new BusinessException("SAME_PASSWORD", "The new password must be different from the current one");
        }
        account.setPasswordHash().accept(passwordEncoder.encode(newPassword));
        sessionService.revokeAll(me.type(), me.id(), me.sessionId()); // keep this device, log out the others
        emailService.queue(account.email(), EmailType.PASSWORD_CHANGED, templates.passwordChanged(account.name(), me.type()));
    }

    // =============== helpers ===============

    private Optional<Account> findByEmail(UserType type, String email) {
        return switch (type) {
            case ADMIN -> adminUserService.findByEmail(email).map(PasswordService::of);
            case PARTNER_USER -> partnerUserService.findByEmail(email).map(PasswordService::of);
            case CUSTOMER -> customerService.findByEmail(email).map(PasswordService::of);
        };
    }

    private Account findById(UserType type, Long id) {
        return switch (type) {
            case ADMIN -> of(adminUserService.getEntity(id));
            case PARTNER_USER -> of(partnerUserService.getEntity(id));
            case CUSTOMER -> of(customerService.getEntity(id));
        };
    }

    private static Account of(AdminUser u) {
        return new Account(UserType.ADMIN, u.getId(), u.getEmail(), u.getFullName(), u.getPasswordHash(), u.getStatus(),
                u::setPasswordHash);
    }

    private static Account of(PartnerUser u) {
        return new Account(UserType.PARTNER_USER, u.getId(), u.getEmail(), u.getFullName(), u.getPasswordHash(),
                u.getStatus(), u::setPasswordHash);
    }

    private static Account of(Customer c) {
        return new Account(UserType.CUSTOMER, c.getId(), c.getEmail(), c.getFirstName(), c.getPasswordHash(),
                c.getStatus(), c::setPasswordHash);
    }

    private static void requireMatch(String password, String confirm) {
        if (!password.equals(confirm)) {
            throw new BusinessException("PASSWORD_MISMATCH", "Passwords do not match");
        }
    }
}
