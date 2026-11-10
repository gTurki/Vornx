package com.example.vornx.security.auth;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.customer.dto.CustomerRegisterRequest;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.partner.entity.PartnerUser;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.service.AdminUserService;
import com.example.vornx.security.TokenService;
import com.example.vornx.security.UserType;
import com.example.vornx.security.auth.dto.AcceptInviteRequest;
import com.example.vornx.security.auth.dto.AuthResponse;
import com.example.vornx.security.auth.dto.LoginChallengeResponse;
import com.example.vornx.security.auth.dto.LoginRequest;
import com.example.vornx.security.otp.LoginChallenge;
import com.example.vornx.security.otp.LoginCodeService;
import com.example.vornx.security.session.AuthSession;
import com.example.vornx.security.session.ClientInfo;
import com.example.vornx.security.session.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Login for the 3 user types. Same steps each time:
 *  1. find the user by email
 *  2. check the password against the stored hash
 *  3. check the account is ACTIVE
 *  4. start a session (refresh token) and issue a short access token
 * On a wrong email OR wrong password we give the SAME message,
 * so attackers can't discover which emails exist.
 *
 * Refresh re-loads the user every time: a suspended account or a changed role takes effect
 * at the next refresh (at most 15 minutes), not "whenever the token expires".
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID = "Invalid email or password";

    private final AdminUserService adminUserService;
    private final PartnerUserService partnerUserService;
    private final CustomerService customerService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SessionService sessionService;
    private final LoginCodeService loginCodeService;

    /** Customer login answer: tokens straight away, OR "a code was sent" (two-step login is on). */
    public record CustomerLogin(AuthResponse tokens, LoginChallengeResponse challenge) {
    }

    /** The current facts about a user, needed to build an access token. */
    private record Principal(Long id, UserType type, String role, Long partnerId) {
    }

    // =============== Login ===============

    @Transactional
    public AuthResponse loginAdmin(LoginRequest req, ClientInfo client) {
        AdminUser admin = adminUserService.findByEmail(normalize(req.email()))
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException(INVALID));
        return startSession(checkAdmin(admin), client);
    }

    @Transactional
    public AuthResponse loginPartnerUser(LoginRequest req, ClientInfo client) {
        PartnerUser user = partnerUserService.findByEmail(normalize(req.email()))
                .filter(u -> u.getPasswordHash() != null && passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException(INVALID));
        Principal principal = checkPartnerUser(user);
        user.setLastLoginAt(Instant.now()); // saved automatically at the end of the transaction
        return startSession(principal, client);
    }

    /**
     * Step 1: email + password. With two-step login on (the default), no tokens yet: a 6-digit code is
     * emailed and the answer is a challenge. A stolen password alone is no longer enough.
     */
    @Transactional
    public CustomerLogin loginCustomer(LoginRequest req, ClientInfo client) {
        Customer customer = customerService.findByEmail(normalize(req.email()))
                .filter(c -> passwordEncoder.matches(req.password(), c.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException(INVALID));
        Principal principal = checkCustomer(customer); // a suspended account gets no code
        if (!loginCodeService.customerCodeRequired()) {
            return new CustomerLogin(startSession(principal, client), null);
        }
        return new CustomerLogin(null, loginCodeService.start(UserType.CUSTOMER, customer.getId(), client.ipAddress()));
    }

    /**
     * Step 2: the code. Right -> tokens. Wrong -> the try is counted and SAVED (noRollbackFor),
     * otherwise rolling back would reset the counter and allow unlimited guesses.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse verifyCustomerCode(String challengeToken, String code, ClientInfo client) {
        LoginChallenge challenge = loginCodeService.verify(challengeToken, code);
        if (challenge.getUserType() != UserType.CUSTOMER) {
            throw new BadCredentialsException(INVALID);
        }
        return startSession(checkCustomer(customerService.getEntity(challenge.getUserId())), client);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginChallengeResponse resendCustomerCode(String challengeToken) {
        return loginCodeService.resend(challengeToken);
    }

    /** Register and log in straight away. */
    @Transactional
    public AuthResponse registerCustomer(CustomerRegisterRequest req, ClientInfo client) {
        return startSession(checkCustomer(customerService.register(req)), client);
    }

    /** Invited partner staff choose their password, then they are logged in straight away. */
    @Transactional
    public AuthResponse acceptPartnerInvite(AcceptInviteRequest req, ClientInfo client) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new BusinessException("PASSWORD_MISMATCH", "Passwords do not match");
        }
        PartnerUser user = partnerUserService.acceptInvite(req.token(), req.password());
        Principal principal = checkPartnerUser(user);
        user.setLastLoginAt(Instant.now());
        return startSession(principal, client);
    }

    // =============== Refresh / logout ===============

    /**
     * New access token + new refresh token. If the account was suspended meanwhile, the session is ended.
     * noRollbackFor: revoking the session (reuse detected, account inactive) must be SAVED even though we throw.
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public AuthResponse refresh(String refreshToken, ClientInfo client) {
        SessionService.Issued rotated = sessionService.rotate(refreshToken, client);
        AuthSession session = rotated.session();
        Principal principal;
        try {
            principal = switch (session.getUserType()) {
                case ADMIN -> checkAdmin(adminUserService.getEntity(session.getUserId()));
                case PARTNER_USER -> checkPartnerUser(partnerUserService.getEntity(session.getUserId()));
                case CUSTOMER -> checkCustomer(customerService.getEntity(session.getUserId()));
            };
        } catch (DisabledException e) {
            sessionService.revoke(session, "ACCOUNT_INACTIVE");
            throw e;
        }
        return response(principal, session, rotated.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        sessionService.logout(refreshToken);
    }

    // =============== helpers ===============

    private AuthResponse startSession(Principal p, ClientInfo client) {
        SessionService.Issued issued = sessionService.start(p.type(), p.id(), client);
        return response(p, issued.session(), issued.refreshToken());
    }

    private AuthResponse response(Principal p, AuthSession session, String refreshToken) {
        String access = tokenService.createAccessToken(p.id(), p.type(), p.role(), p.partnerId(), session.getId());
        return new AuthResponse(access, "Bearer", tokenService.accessTokenSeconds(), refreshToken,
                tokenService.refreshTokenSeconds(), p.type(), p.role(), session.getId());
    }

    private static Principal checkAdmin(AdminUser admin) {
        ensureActive(admin.getStatus());
        return new Principal(admin.getId(), UserType.ADMIN, admin.getRole().name(), null);
    }

    private static Principal checkPartnerUser(PartnerUser user) {
        ensureActive(user.getStatus());
        if (user.getPartner().getStatus() == PartnerStatus.SUSPENDED) {
            throw new DisabledException("Your organization's account is suspended");
        }
        return new Principal(user.getId(), UserType.PARTNER_USER, user.getRole().name(), user.getPartner().getId());
    }

    private static Principal checkCustomer(Customer customer) {
        ensureActive(customer.getStatus());
        return new Principal(customer.getId(), UserType.CUSTOMER, null, null);
    }

    private static void ensureActive(AccountStatus status) {
        if (status != AccountStatus.ACTIVE) {
            throw new DisabledException("Account is not active");
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
