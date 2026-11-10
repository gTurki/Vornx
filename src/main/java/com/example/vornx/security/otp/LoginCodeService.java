package com.example.vornx.security.otp;

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
import com.example.vornx.security.UserType;
import com.example.vornx.security.auth.dto.LoginChallengeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The one-time code of the two-step login (step 1 = password, step 2 = this code).
 *
 * Security rules:
 *  - 6 random digits, valid 5 minutes; the whole challenge lives 15 minutes.
 *  - 5 wrong codes and the challenge is burned: log in again with the password (so guessing 1 in 1,000,000
 *    needs the password every 5 tries).
 *  - "send again" at most every 60 s and 3 sends per challenge; at most 5 logins per account per 15 minutes
 *    (nobody can flood a customer's inbox or SMS bill).
 *  - only hashes are stored; the code hash is salted with the challenge token the browser holds.
 *  - a new login replaces older unfinished ones; a used code never works twice.
 *
 * Channel today: EMAIL (through the outbox). SMS is added later as another LoginCodeChannel.
 */
@Slf4j
@Service
// MANDATORY: always part of the caller's login transaction. noRollbackFor: a wrong code must not mark that
// transaction "rollback only", or the attempt counter would never be saved (and the caller would get a 500).
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
public class LoginCodeService {

    static final String PREFIX = "lc_";
    static final Duration CODE_VALIDITY = Duration.ofMinutes(5);
    static final Duration CHALLENGE_VALIDITY = Duration.ofMinutes(15);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration START_WINDOW = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_SENDS = 3;
    static final int MAX_STARTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoginChallengeRepository repository;
    private final CustomerService customerService;
    private final PartnerUserService partnerUserService;
    private final AdminUserService adminUserService;
    private final EmailService emailService;
    private final EmailTemplates templates;
    private final Clock clock;
    private final boolean customerCodeRequired;
    private final boolean exposeCodes;

    public LoginCodeService(LoginChallengeRepository repository, CustomerService customerService,
                            PartnerUserService partnerUserService, AdminUserService adminUserService,
                            EmailService emailService, EmailTemplates templates, Clock clock,
                            @Value("${vornx.auth.customer-login-code:email}") String customerMode,
                            @Value("${vornx.dev.expose-tokens:false}") boolean exposeCodes) {
        this.repository = repository;
        this.customerService = customerService;
        this.partnerUserService = partnerUserService;
        this.adminUserService = adminUserService;
        this.emailService = emailService;
        this.templates = templates;
        this.clock = clock;
        this.customerCodeRequired = switch (customerMode.trim().toLowerCase()) {
            case "email" -> true;
            case "off" -> false;
            default -> throw new IllegalStateException("vornx.auth.customer-login-code must be 'email' or 'off'");
        };
        this.exposeCodes = exposeCodes;
    }

    /** Who receives the code. */
    private record Recipient(String email, String name) {
    }

    public boolean customerCodeRequired() {
        return customerCodeRequired;
    }

    // =============== Step 1 done (right password): send a code ===============

    public LoginChallengeResponse start(UserType type, Long userId, String ipAddress) {
        Instant now = Instant.now(clock);
        if (repository.countByUserTypeAndUserIdAndCreatedAtAfter(type, userId, now.minus(START_WINDOW)) >= MAX_STARTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please wait a few minutes and try again.");
        }
        repository.consumeOpen(type, userId, now); // an older code (other tab, earlier try) stops working

        String token = PREFIX + SecureTokens.generate();
        String code = newCode();
        LoginChallenge challenge = repository.save(new LoginChallenge(type, userId, LoginCodeChannel.EMAIL,
                SecureTokens.sha256(token), codeHash(token, code), now, now.plus(CODE_VALIDITY),
                now.plus(CHALLENGE_VALIDITY), ipAddress));
        Recipient to = recipient(type, userId);
        send(to, code);
        return response(token, challenge, to, now, code);
    }

    // =============== "Send the code again" ===============

    public LoginChallengeResponse resend(String token) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now);
        Duration wait = Duration.between(now, challenge.getLastSentAt().plus(RESEND_COOLDOWN));
        if (!wait.isNegative() && !wait.isZero()) {
            throw new BusinessException("CODE_RESEND_TOO_SOON", "Please wait before asking for a new code",
                    Map.of("resendIn", Math.max(1, wait.toSeconds())));
        }
        if (challenge.getSends() >= MAX_SENDS) {
            throw new BusinessException("CODE_RESEND_LIMIT", "No more codes for this login. Log in again.");
        }
        String code = newCode();
        challenge.resend(codeHash(token, code), now, now.plus(CODE_VALIDITY));
        Recipient to = recipient(challenge.getUserType(), challenge.getUserId());
        send(to, code);
        return response(token, challenge, to, now, code);
    }

    // =============== Step 2: check the code ===============

    /**
     * Returns the challenge (user type + id) when the code is right; it can never be used again.
     * A wrong code is COUNTED, so the caller's transaction must not roll back on BusinessException.
     */
    public LoginChallenge verify(String token, String code) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now);
        if (challenge.isCodeExpired(now)) {
            throw new BusinessException("CODE_EXPIRED", "This code has expired. Ask for a new one.");
        }
        if (code == null || !matches(codeHash(token, code.trim()), challenge.getCodeHash())) {
            int left = challenge.failAttempt(now, MAX_ATTEMPTS);
            if (left == 0) {
                throw new BusinessException("LOGIN_CHALLENGE_INVALID", "Too many wrong codes. Log in again.");
            }
            throw new BusinessException("CODE_INVALID", "The code is not correct", Map.of("attemptsLeft", left));
        }
        challenge.consume(now);
        return challenge;
    }

    /** Nightly: old challenges are useless after 15 minutes; keep a day for support questions. */
    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Riyadh")
    @Transactional
    public void deleteOld() {
        int deleted = repository.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofDays(1)));
        if (deleted > 0) {
            log.info("Deleted {} old login challenge(s)", deleted);
        }
    }

    // =============== helpers ===============

    private LoginChallenge open(String token, Instant now) {
        return (token != null && token.startsWith(PREFIX)
                ? repository.lockByHash(SecureTokens.sha256(token)) : Optional.<LoginChallenge>empty())
                .filter(c -> c.isOpen(now))
                .orElseThrow(() -> new BusinessException("LOGIN_CHALLENGE_INVALID",
                        "This login step has expired. Log in again."));
    }

    private void send(Recipient to, String code) {
        emailService.queue(to.email(), EmailType.LOGIN_CODE,
                templates.loginCode(to.name(), code, (int) CODE_VALIDITY.toMinutes()));
    }

    private LoginChallengeResponse response(String token, LoginChallenge c, Recipient to, Instant now, String code) {
        return new LoginChallengeResponse("CODE_REQUIRED", token, c.getChannel(), maskEmail(to.email()),
                Duration.between(now, c.getCodeExpiresAt()).toSeconds(), RESEND_COOLDOWN.toSeconds(),
                MAX_SENDS - c.getSends(), MAX_ATTEMPTS - c.getAttempts(),
                exposeCodes ? code : null);
    }

    private Recipient recipient(UserType type, Long id) {
        return switch (type) {
            case CUSTOMER -> {
                Customer c = customerService.getEntity(id);
                yield new Recipient(c.getEmail(), c.getFirstName());
            }
            case PARTNER_USER -> {
                PartnerUser u = partnerUserService.getEntity(id);
                yield new Recipient(u.getEmail(), u.getFullName());
            }
            case ADMIN -> {
                AdminUser a = adminUserService.getEntity(id);
                yield new Recipient(a.getEmail(), a.getFullName());
            }
        };
    }

    static String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    static String codeHash(String token, String code) {
        return SecureTokens.sha256(token + ":" + code);
    }

    /** Constant-time compare: the answer time doesn't reveal how many characters matched. */
    private static boolean matches(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    /** "trroky007@gmail.com" -> "tr•••7@gmail.com": enough to recognize, not enough to harvest. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "•••";
        }
        String local = email.substring(0, at);
        String shown = local.length() <= 2 ? local.substring(0, 1) + "•••"
                : local.substring(0, 2) + "•••" + local.charAt(local.length() - 1);
        return shown + email.substring(at);
    }
}
