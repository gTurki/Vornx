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
 * Two channels:
 *  - EMAIL: we make the code and email it.
 *  - SMS:   Firebase makes the code and texts it (from the customer app); the app sends us Firebase's signed
 *           proof and we check it is for the customer's own number. If the SMS doesn't come (or the free
 *           daily SMS are used up) the customer switches the SAME login to email with one tap.
 *
 * Security rules:
 *  - 6 random digits, valid 5 minutes; the whole challenge lives 15 minutes.
 *  - 5 wrong codes and the challenge is burned: log in again with the password (so guessing 1 in 1,000,000
 *    needs the password every 5 tries).
 *  - "send again" at most every 60 s and 3 sends per challenge; at most 5 logins per account per 15 minutes
 *    (nobody can flood a customer's inbox or SMS bill).
 *  - only hashes are stored; the code hash is salted with the challenge token the browser holds.
 *  - a new login replaces older unfinished ones; a used code never works twice.
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

    /** vornx.auth.customer-login-code */
    enum Mode { OFF, EMAIL, SMS }

    private final LoginChallengeRepository repository;
    private final CustomerService customerService;
    private final PartnerUserService partnerUserService;
    private final AdminUserService adminUserService;
    private final EmailService emailService;
    private final EmailTemplates templates;
    private final FirebasePhoneVerifier firebase;
    private final Clock clock;
    private final Mode customerMode;
    private final boolean exposeCodes;

    public LoginCodeService(LoginChallengeRepository repository, CustomerService customerService,
                            PartnerUserService partnerUserService, AdminUserService adminUserService,
                            EmailService emailService, EmailTemplates templates, FirebasePhoneVerifier firebase,
                            Clock clock,
                            @Value("${vornx.auth.customer-login-code:email}") String customerMode,
                            @Value("${vornx.dev.expose-tokens:false}") boolean exposeCodes) {
        this.repository = repository;
        this.customerService = customerService;
        this.partnerUserService = partnerUserService;
        this.adminUserService = adminUserService;
        this.emailService = emailService;
        this.templates = templates;
        this.firebase = firebase;
        this.clock = clock;
        this.customerMode = switch (customerMode.trim().toLowerCase()) {
            case "email" -> Mode.EMAIL;
            case "sms" -> Mode.SMS;
            case "off" -> Mode.OFF;
            default -> throw new IllegalStateException("vornx.auth.customer-login-code must be 'sms', 'email' or 'off'");
        };
        if (this.customerMode == Mode.SMS && !firebase.isConfigured()) {
            // fail at startup, not at the first customer's login
            throw new IllegalStateException("CUSTOMER_LOGIN_CODE=sms needs FIREBASE_PROJECT_ID");
        }
        this.exposeCodes = exposeCodes;
    }

    /** Who receives the code. phone is E.164 (+9665...) or null. */
    private record Recipient(String email, String name, String phone) {
    }

    public boolean customerCodeRequired() {
        return customerMode != Mode.OFF;
    }

    // =============== Step 1 done (right password): send a code ===============

    public LoginChallengeResponse start(UserType type, Long userId, String ipAddress) {
        Instant now = Instant.now(clock);
        if (repository.countByUserTypeAndUserIdAndCreatedAtAfter(type, userId, now.minus(START_WINDOW)) >= MAX_STARTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please wait a few minutes and try again.");
        }
        repository.consumeOpen(type, userId, now); // an older code (other tab, earlier try) stops working

        Recipient to = recipient(type, userId);
        boolean sms = type == UserType.CUSTOMER && customerMode == Mode.SMS && to.phone() != null;
        String token = PREFIX + SecureTokens.generate();
        // SMS: Firebase owns the code, so our code hash is random (no typed code can match it).
        String code = sms ? null : newCode();
        LoginChallenge challenge = repository.save(new LoginChallenge(type, userId,
                sms ? LoginCodeChannel.SMS : LoginCodeChannel.EMAIL, SecureTokens.sha256(token),
                sms ? SecureTokens.sha256(SecureTokens.generate()) : codeHash(token, code),
                now, now.plus(CODE_VALIDITY), now.plus(CHALLENGE_VALIDITY), ipAddress));
        if (!sms) {
            sendEmail(to, code);
        }
        return response(token, challenge, to, now, code);
    }

    // =============== "Send the code again" (or: "send it by email instead") ===============

    public LoginChallengeResponse resend(String token) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now);
        if (challenge.getSends() >= MAX_SENDS) {
            throw new BusinessException("CODE_RESEND_LIMIT", "No more codes for this login. Log in again.");
        }
        boolean fromSms = challenge.getChannel() == LoginCodeChannel.SMS;
        // Switching from SMS to email is allowed straight away: the SMS may never come.
        Duration wait = Duration.between(now, challenge.getLastSentAt().plus(RESEND_COOLDOWN));
        if (!fromSms && !wait.isNegative() && !wait.isZero()) {
            throw new BusinessException("CODE_RESEND_TOO_SOON", "Please wait before asking for a new code",
                    Map.of("resendIn", Math.max(1, wait.toSeconds())));
        }
        String code = newCode();
        if (fromSms) {
            challenge.switchToEmail(codeHash(token, code), now, now.plus(CODE_VALIDITY));
        } else {
            challenge.resend(codeHash(token, code), now, now.plus(CODE_VALIDITY));
        }
        Recipient to = recipient(challenge.getUserType(), challenge.getUserId());
        sendEmail(to, code);
        return response(token, challenge, to, now, code);
    }

    // =============== Step 2: check the code ===============

    /**
     * EMAIL channel: the typed code. Returns the challenge (user type + id) when right; it can never be used again.
     * A wrong code is COUNTED, so the caller's transaction must not roll back on BusinessException.
     */
    public LoginChallenge verify(String token, String code) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now);
        if (challenge.isCodeExpired(now)) {
            throw new BusinessException("CODE_EXPIRED", "This code has expired. Ask for a new one.");
        }
        if (code == null || !matches(codeHash(token, code.trim()), challenge.getCodeHash())) {
            throw wrong(challenge, now, "CODE_INVALID", "The code is not correct");
        }
        challenge.consume(now);
        return challenge;
    }

    /** SMS channel: Firebase's signed proof that the customer typed the SMS code sent to THEIR number. */
    public LoginChallenge verifyPhone(String token, String firebaseIdToken) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now);
        if (challenge.getChannel() != LoginCodeChannel.SMS) {
            throw new BusinessException("PHONE_VERIFICATION_FAILED", "This login continues by email");
        }
        String verified;
        try {
            verified = firebase.verifiedPhone(firebaseIdToken);
        } catch (BusinessException e) {
            throw wrong(challenge, now, e.getCode(), e.getMessage());
        }
        String expected = recipient(challenge.getUserType(), challenge.getUserId()).phone();
        if (expected == null || !expected.equals(verified)) {
            // Someone verified A phone, just not this customer's.
            log.warn("Login challenge {}: Firebase phone does not match the account", challenge.getId());
            throw wrong(challenge, now, "PHONE_MISMATCH", "This phone number does not belong to the account");
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

    /** Counts a failed try; the 5th burns the challenge. */
    private static BusinessException wrong(LoginChallenge challenge, Instant now, String code, String message) {
        int left = challenge.failAttempt(now, MAX_ATTEMPTS);
        if (left == 0) {
            return new BusinessException("LOGIN_CHALLENGE_INVALID", "Too many wrong codes. Log in again.");
        }
        return new BusinessException(code, message, Map.of("attemptsLeft", left));
    }

    private void sendEmail(Recipient to, String code) {
        emailService.queue(to.email(), EmailType.LOGIN_CODE,
                templates.loginCode(to.name(), code, (int) CODE_VALIDITY.toMinutes()));
    }

    private LoginChallengeResponse response(String token, LoginChallenge c, Recipient to, Instant now, String code) {
        boolean sms = c.getChannel() == LoginCodeChannel.SMS;
        return new LoginChallengeResponse("CODE_REQUIRED", token, c.getChannel(),
                sms ? maskPhone(to.phone()) : maskEmail(to.email()),
                sms ? to.phone() : null,
                Duration.between(now, c.getCodeExpiresAt()).toSeconds(), RESEND_COOLDOWN.toSeconds(),
                MAX_SENDS - c.getSends(), MAX_ATTEMPTS - c.getAttempts(),
                exposeCodes ? code : null);
    }

    private Recipient recipient(UserType type, Long id) {
        return switch (type) {
            case CUSTOMER -> {
                Customer c = customerService.getEntity(id);
                yield new Recipient(c.getEmail(), c.getFirstName(), toE164(c.getPhone()));
            }
            case PARTNER_USER -> {
                PartnerUser u = partnerUserService.getEntity(id);
                yield new Recipient(u.getEmail(), u.getFullName(), null);
            }
            case ADMIN -> {
                AdminUser a = adminUserService.getEntity(id);
                yield new Recipient(a.getEmail(), a.getFullName(), null);
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

    /** Saudi mobile as stored at sign-up ("0551112233") -> E.164 ("+966551112233"), the format Firebase uses. */
    static String toE164(String phone) {
        if (phone == null) {
            return null;
        }
        String p = phone.replaceAll("[\\s-]", "");
        if (p.matches("05\\d{8}")) {
            return "+966" + p.substring(1);
        }
        if (p.matches("\\+9665\\d{8}")) {
            return p;
        }
        return null; // unknown format: this customer gets the email code
    }

    /** "+966551112233" -> "+966 5•••••233" */
    static String maskPhone(String e164) {
        return e164.substring(0, 4) + " " + e164.charAt(4) + "•••••" + e164.substring(e164.length() - 3);
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
