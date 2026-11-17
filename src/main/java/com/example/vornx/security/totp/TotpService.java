package com.example.vornx.security.totp;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The customer's authenticator app (optional, turned on in "حسابي").
 * Once on, the login's second step is the app's 6-digit code instead of the emailed one: someone who controls
 * the customer's email still can't log in.
 *
 * Setup: 1) setup() makes a secret -> the app shows it as a QR code, the customer scans it;
 *        2) enable(code) proves the scan worked -> turned on, and 10 one-time recovery codes are shown ONCE.
 * Lost phone: a recovery code replaces the app's code once. Turning it off needs the password.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TotpService {

    static final int RECOVERY_CODES = 10;
    private static final String ISSUER = "VORNX";
    /** No 0/O, 1/I: easy to copy by hand. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerService customerService;
    private final RecoveryCodeRepository recoveryCodes;
    private final SecretBox box;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailTemplates templates;
    private final Clock clock;

    public record Setup(String secret, String otpauthUri) {
    }

    public record Status(boolean enabled, Instant enabledAt, long recoveryCodesLeft) {
    }

    @Transactional(readOnly = true)
    public Status status(Long customerId) {
        Customer c = customerService.getEntity(customerId);
        return new Status(c.isTotpEnabled(), c.getTotpEnabledAt(),
                c.isTotpEnabled() ? recoveryCodes.countByCustomerIdAndUsedAtIsNull(customerId) : 0);
    }

    /** Step 1: a new secret (replaces an unfinished setup). */
    public Setup setup(Long customerId) {
        Customer c = customerService.getEntity(customerId);
        if (c.isTotpEnabled()) {
            throw new BusinessException("TOTP_ALREADY_ON", "The authenticator app is already turned on");
        }
        String secret = Totp.newSecret();
        c.setTotpPendingSecret(box.encrypt(secret));
        return new Setup(secret, Totp.uri(ISSUER, c.getEmail(), secret));
    }

    /** Step 2: the first code from the app. Returns the recovery codes (shown once, never again). */
    @Transactional(noRollbackFor = BusinessException.class)
    public List<String> enable(Long customerId, String code) {
        Customer c = customerService.getEntity(customerId);
        if (c.isTotpEnabled()) {
            throw new BusinessException("TOTP_ALREADY_ON", "The authenticator app is already turned on");
        }
        if (c.getTotpPendingSecret() == null) {
            throw new BusinessException("TOTP_SETUP_NEEDED", "Start the setup again");
        }
        Instant now = Instant.now(clock);
        String secret = box.decrypt(c.getTotpPendingSecret());
        long step = Totp.match(secret, code, now, null);
        if (step < 0) {
            throw new BusinessException("TOTP_CODE_INVALID", "The code is not correct. Check the time on your phone.");
        }
        c.setTotpSecret(c.getTotpPendingSecret());
        c.setTotpPendingSecret(null);
        c.setTotpEnabledAt(now);
        c.setTotpLastStep(step);

        recoveryCodes.deleteAllForCustomer(customerId);
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String raw = newRecoveryCode();
            recoveryCodes.save(new RecoveryCode(customerId, recoveryHash(raw)));
            shown.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        emailService.queue(c.getEmail(), EmailType.SECURITY_ALERT, templates.totpChanged(c.getFirstName(), true));
        return shown;
    }

    public void disable(Long customerId, String password) {
        Customer c = customerService.getEntity(customerId);
        if (password == null || !passwordEncoder.matches(password, c.getPasswordHash())) {
            throw new BusinessException("WRONG_PASSWORD", "Your current password is not correct");
        }
        if (!c.isTotpEnabled()) {
            return;
        }
        c.setTotpSecret(null);
        c.setTotpPendingSecret(null);
        c.setTotpEnabledAt(null);
        c.setTotpLastStep(null);
        recoveryCodes.deleteAllForCustomer(customerId);
        emailService.queue(c.getEmail(), EmailType.SECURITY_ALERT, templates.totpChanged(c.getFirstName(), false));
    }

    /**
     * Login step 2: the app's 6-digit code, or a recovery code ("ABCDE-FGH23"). True = accepted (and used up).
     * Runs inside the login transaction.
     */
    public boolean checkLogin(Customer c, String typed) {
        if (!c.isTotpEnabled() || typed == null) {
            return false;
        }
        Instant now = Instant.now(clock);
        String code = typed.trim();
        if (code.matches("\\d{6}")) {
            long step = Totp.match(box.decrypt(c.getTotpSecret()), code, now, c.getTotpLastStep());
            if (step < 0) {
                return false;
            }
            c.setTotpLastStep(step);
            return true;
        }
        String normalized = code.toUpperCase().replaceAll("[\\s-]", "");
        if (normalized.length() != 10) {
            return false;
        }
        return recoveryCodes.findByCustomerIdAndCodeHashAndUsedAtIsNull(c.getId(), recoveryHash(normalized))
                .map(r -> {
                    long left = recoveryCodes.countByCustomerIdAndUsedAtIsNull(c.getId()) - 1; // before marking it used
                    r.use(now);
                    emailService.queue(c.getEmail(), EmailType.SECURITY_ALERT, templates.recoveryCodeUsed(c.getFirstName(), left));
                    return true;
                })
                .orElse(false);
    }

    static String newRecoveryCode() {
        char[] out = new char[10];
        for (int i = 0; i < out.length; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }

    static String recoveryHash(String normalized) {
        return SecureTokens.sha256("vornx-recovery:" + normalized);
    }
}
