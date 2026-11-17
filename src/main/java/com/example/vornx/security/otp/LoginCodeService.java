package com.example.vornx.security.otp;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.customer.service.PhoneVerificationPolicy;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.partner.entity.PartnerUser;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.service.AdminUserService;
import com.example.vornx.security.UserType;
import com.example.vornx.security.auth.dto.LoginChallengeResponse;
import com.example.vornx.security.totp.TotpService;
import com.example.vornx.whatsapp.WhatsAppSender;
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
import java.util.Set;

/**
 * One-time codes: the second step of a login, the email check right after sign-up, and the mobile check.
 *
 * Purposes (a code only works for its own purpose):
 *  - LOGIN:  after the right password. Channel: EMAIL, or TOTP when the customer turned the authenticator app on
 *            (only once the email is confirmed), or SMS (Firebase) in sms mode.
 *  - SIGNUP: confirm the email right after sign-up (EMAIL); the right code also logs in.
 *  - PHONE:  a logged-in customer confirms their mobile with a code sent by WHATSAPP.
 * Any right EMAIL code confirms the email (it proves the customer reads that inbox).
 *
 * Security rules:
 *  - 6 random digits, valid 5 minutes; the whole challenge lives 15 minutes.
 *  - 5 wrong codes and the challenge is burned (log in / ask again).
 *  - "send again" at most every 60 s and 3 sends per challenge; at most 5 challenges per purpose per account
 *    per 15 minutes (nobody can flood a customer's inbox or WhatsApp).
 *  - only hashes are stored; the code hash is salted with the challenge token the browser holds.
 *  - a new challenge replaces older unfinished ones of the same kind; a used code never works twice.
 */
@Slf4j
@Service
// MANDATORY: always part of the caller's transaction. noRollbackFor: a wrong code must not mark that
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

    /** Codes that can log in. A PHONE code never can. */
    private static final Set<CodePurpose> LOGIN_PURPOSES = Set.of(CodePurpose.LOGIN, CodePurpose.SIGNUP);
    private static final Set<CodePurpose> PHONE_PURPOSE = Set.of(CodePurpose.PHONE);

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
    private final TotpService totp;
    private final WhatsAppSender whatsApp;
    private final PhoneVerificationPolicy phonePolicy;
    private final Clock clock;
    private final Mode customerMode;
    private final boolean exposeCodes;

    public LoginCodeService(LoginChallengeRepository repository, CustomerService customerService,
                            PartnerUserService partnerUserService, AdminUserService adminUserService,
                            EmailService emailService, EmailTemplates templates, FirebasePhoneVerifier firebase,
                            TotpService totp, WhatsAppSender whatsApp, PhoneVerificationPolicy phonePolicy,
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
        this.totp = totp;
        this.whatsApp = whatsApp;
        this.phonePolicy = phonePolicy;
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

    /** Who receives the code. phone is E.164 (+9665...) or null. customer is null for staff. */
    private record Recipient(String email, String name, String phone, Customer customer) {
    }

    /**
     * Does this customer's login need a second step? Always when the email isn't confirmed yet (that code confirms
     * it) or the authenticator app is on; otherwise unless two-step login is turned off.
     */
    public boolean customerCodeRequired(Customer c) {
        return customerMode != Mode.OFF || !c.isEmailVerified() || c.isTotpEnabled();
    }

    // =============== Start: send a code ===============

    /** Login step 1 done (right password). */
    public LoginChallengeResponse start(UserType type, Long userId, String ipAddress) {
        return begin(type, userId, CodePurpose.LOGIN, ipAddress);
    }

    /** Right after sign-up: confirm the email. */
    public LoginChallengeResponse startSignup(Long customerId, String ipAddress) {
        return begin(UserType.CUSTOMER, customerId, CodePurpose.SIGNUP, ipAddress);
    }

    /** Logged-in customer: confirm the mobile by WhatsApp. */
    public LoginChallengeResponse startPhone(Long customerId, String ipAddress) {
        return begin(UserType.CUSTOMER, customerId, CodePurpose.PHONE, ipAddress);
    }

    private LoginChallengeResponse begin(UserType type, Long userId, CodePurpose purpose, String ipAddress) {
        Instant now = Instant.now(clock);
        Recipient to = recipient(type, userId);
        if (purpose == CodePurpose.PHONE) {
            checkPhoneCanBeVerified(to);
        }
        if (repository.countByUserTypeAndUserIdAndPurposeAndCreatedAtAfter(type, userId, purpose,
                now.minus(START_WINDOW)) >= MAX_STARTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Please wait a few minutes and try again.");
        }
        // an older code (other tab, earlier try) stops working
        repository.consumeOpen(type, userId, purpose == CodePurpose.PHONE ? PHONE_PURPOSE : LOGIN_PURPOSES, now);

        LoginCodeChannel channel = channel(type, purpose, to);
        String token = PREFIX + SecureTokens.generate();
        // SMS / TOTP: the code isn't ours, so our code hash is random (no typed code can match it).
        String code = channel == LoginCodeChannel.EMAIL || channel == LoginCodeChannel.WHATSAPP ? newCode() : null;
        LoginChallenge challenge = repository.save(new LoginChallenge(type, userId, purpose, channel,
                purpose == CodePurpose.PHONE ? to.phone() : null, SecureTokens.sha256(token),
                code != null ? codeHash(token, code) : SecureTokens.sha256(SecureTokens.generate()),
                now, now.plus(CODE_VALIDITY), now.plus(CHALLENGE_VALIDITY), ipAddress));
        deliver(challenge, to, code);
        return response(token, challenge, to, now, code);
    }

    private LoginCodeChannel channel(UserType type, CodePurpose purpose, Recipient to) {
        if (purpose == CodePurpose.PHONE) {
            return LoginCodeChannel.WHATSAPP;
        }
        Customer c = to.customer();
        if (purpose == CodePurpose.SIGNUP || c == null || !c.isEmailVerified()) {
            return LoginCodeChannel.EMAIL; // staff, or the email still has to be confirmed
        }
        if (c.isTotpEnabled()) {
            return LoginCodeChannel.TOTP;
        }
        return customerMode == Mode.SMS && to.phone() != null ? LoginCodeChannel.SMS : LoginCodeChannel.EMAIL;
    }

    private void checkPhoneCanBeVerified(Recipient to) {
        if (!phonePolicy.isEnabled()) {
            throw new BusinessException("PHONE_VERIFICATION_OFF", "Mobile verification is not available");
        }
        if (to.customer().isPhoneVerified()) {
            throw new BusinessException("PHONE_ALREADY_VERIFIED", "Your mobile number is already verified");
        }
        if (to.phone() == null) {
            throw new BusinessException("PHONE_INVALID", "Your mobile number can't receive codes");
        }
    }

    // =============== "Send the code again" (or: "send it by email instead") ===============

    /** Login / sign-up (public endpoint). */
    public LoginChallengeResponse resend(String token) {
        return resend(token, LOGIN_PURPOSES, null);
    }

    /** Mobile check: only the logged-in customer who started it. */
    public LoginChallengeResponse resendPhone(String token, Long customerId) {
        return resend(token, PHONE_PURPOSE, customerId);
    }

    private LoginChallengeResponse resend(String token, Set<CodePurpose> purposes, Long customerId) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now, purposes, customerId);
        if (challenge.getChannel() == LoginCodeChannel.TOTP) {
            throw new BusinessException("CODE_RESEND_NOT_AVAILABLE",
                    "Use the code from your authenticator app, or a recovery code");
        }
        if (challenge.getSends() >= MAX_SENDS) {
            throw new BusinessException("CODE_RESEND_LIMIT", "No more codes for this attempt. Start again.");
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
        deliver(challenge, to, code);
        return response(token, challenge, to, now, code);
    }

    // =============== Check the code ===============

    /**
     * Login / sign-up: the typed code (email code, authenticator code, or a recovery code).
     * Returns the challenge (user type + id) when right; it can never be used again.
     * A wrong code is COUNTED, so the caller's transaction must not roll back on BusinessException.
     */
    public LoginChallenge verify(String token, String code) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now, LOGIN_PURPOSES, null);
        if (challenge.getChannel() == LoginCodeChannel.TOTP) {
            Customer c = customerService.getEntity(challenge.getUserId());
            if (!totp.checkLogin(c, code)) {
                throw wrong(challenge, now, "CODE_INVALID", "The code is not correct");
            }
            challenge.consume(now);
            return challenge;
        }
        checkTypedCode(challenge, token, code, now);
        challenge.consume(now);
        if (challenge.getUserType() == UserType.CUSTOMER) {
            Customer c = customerService.getEntity(challenge.getUserId());
            boolean first = c.markEmailVerified(now); // they read that inbox
            if (first && challenge.getPurpose() == CodePurpose.SIGNUP) {
                emailService.queue(c.getEmail(), EmailType.CUSTOMER_WELCOME,
                        templates.customerWelcome(c.getFirstName(), c.getVornxId()));
            }
        }
        return challenge;
    }

    /** Mobile check: the code from WhatsApp. Marks the number verified. */
    public Customer verifyPhoneCode(String token, String code, Long customerId) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now, PHONE_PURPOSE, customerId);
        checkTypedCode(challenge, token, code, now);
        challenge.consume(now);
        Customer c = customerService.getEntity(customerId);
        if (challenge.getDestination() == null || !challenge.getDestination().equals(toE164(c.getPhone()))) {
            throw new BusinessException("PHONE_CHANGED", "Your mobile number changed. Ask for a new code.");
        }
        c.markPhoneVerified(now);
        return c;
    }

    /** SMS channel: Firebase's signed proof that the customer typed the SMS code sent to THEIR number. */
    public LoginChallenge verifyPhone(String token, String firebaseIdToken) {
        Instant now = Instant.now(clock);
        LoginChallenge challenge = open(token, now, LOGIN_PURPOSES, null);
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

    private void checkTypedCode(LoginChallenge challenge, String token, String code, Instant now) {
        if (challenge.isCodeExpired(now)) {
            throw new BusinessException("CODE_EXPIRED", "This code has expired. Ask for a new one.");
        }
        if (code == null || !matches(codeHash(token, code.trim()), challenge.getCodeHash())) {
            throw wrong(challenge, now, "CODE_INVALID", "The code is not correct");
        }
    }

    /** The open challenge for this token, of an allowed purpose (and, if given, of this customer). */
    private LoginChallenge open(String token, Instant now, Set<CodePurpose> purposes, Long customerId) {
        return (token != null && token.startsWith(PREFIX)
                ? repository.lockByHash(SecureTokens.sha256(token)) : Optional.<LoginChallenge>empty())
                .filter(c -> c.isOpen(now) && purposes.contains(c.getPurpose()))
                .filter(c -> customerId == null
                        || (c.getUserType() == UserType.CUSTOMER && customerId.equals(c.getUserId())))
                .orElseThrow(() -> new BusinessException("LOGIN_CHALLENGE_INVALID",
                        "This step has expired. Start again."));
    }

    /** Counts a failed try; the 5th burns the challenge. */
    private static BusinessException wrong(LoginChallenge challenge, Instant now, String code, String message) {
        int left = challenge.failAttempt(now, MAX_ATTEMPTS);
        if (left == 0) {
            return new BusinessException("LOGIN_CHALLENGE_INVALID", "Too many wrong codes. Start again.");
        }
        return new BusinessException(code, message, Map.of("attemptsLeft", left));
    }

    private void deliver(LoginChallenge challenge, Recipient to, String code) {
        int minutes = (int) CODE_VALIDITY.toMinutes();
        switch (challenge.getChannel()) {
            case EMAIL -> {
                if (challenge.getPurpose() == CodePurpose.SIGNUP) {
                    emailService.queue(to.email(), EmailType.EMAIL_VERIFICATION,
                            templates.emailVerification(to.name(), code, minutes));
                } else {
                    emailService.queue(to.email(), EmailType.LOGIN_CODE, templates.loginCode(to.name(), code, minutes));
                }
            }
            case WHATSAPP -> whatsApp.send(challenge.getDestination(), whatsAppText(code, minutes));
            case SMS, TOTP -> {
                // nothing to send: Firebase texts the SMS; the authenticator app shows its own code
            }
        }
    }

    /**
     * Short, code first (it shows in the notification preview), Arabic then English. The Arabic line starts with
     * the code and an Arabic word, so WhatsApp lays it out right-to-left; "[VORNX]" leads only the English line.
     * No *bold*: some phones show the asterisks in the notification.
     */
    static String whatsAppText(String code, int minutes) {
        return code + " هو رمز التحقق الخاص بك في VORNX. صالح لمدة " + minutes
                + " دقائق. لحماية حسابك، لا تشارك هذا الرمز مع أي أحد.\n\n"
                + "[VORNX] " + code + " is your verification code, valid for " + minutes
                + " minutes. To protect your account, never share this code with anyone.";
    }

    private LoginChallengeResponse response(String token, LoginChallenge c, Recipient to, Instant now, String code) {
        LoginCodeChannel ch = c.getChannel();
        boolean totpChannel = ch == LoginCodeChannel.TOTP;
        String destination = switch (ch) {
            case SMS, WHATSAPP -> maskPhone(to.phone());
            case EMAIL -> maskEmail(to.email());
            case TOTP -> null;
        };
        return new LoginChallengeResponse("CODE_REQUIRED", token, ch, c.getPurpose(), destination,
                ch == LoginCodeChannel.SMS ? to.phone() : null,
                Duration.between(now, totpChannel ? c.getExpiresAt() : c.getCodeExpiresAt()).toSeconds(),
                totpChannel ? 0 : RESEND_COOLDOWN.toSeconds(),
                totpChannel ? 0 : MAX_SENDS - c.getSends(), MAX_ATTEMPTS - c.getAttempts(),
                exposeCodes ? code : null);
    }

    private Recipient recipient(UserType type, Long id) {
        return switch (type) {
            case CUSTOMER -> {
                Customer c = customerService.getEntity(id);
                yield new Recipient(c.getEmail(), c.getFirstName(), toE164(c.getPhone()), c);
            }
            case PARTNER_USER -> {
                PartnerUser u = partnerUserService.getEntity(id);
                yield new Recipient(u.getEmail(), u.getFullName(), null, null);
            }
            case ADMIN -> {
                AdminUser a = adminUserService.getEntity(id);
                yield new Recipient(a.getEmail(), a.getFullName(), null, null);
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

    /** Saudi mobile as stored at sign-up ("0551112233") -> E.164 ("+966551112233"). */
    public static String toE164(String phone) {
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
        return null; // unknown format
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
