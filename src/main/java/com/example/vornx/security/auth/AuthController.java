package com.example.vornx.security.auth;

import com.example.vornx.customer.dto.CustomerRegisterRequest;
import com.example.vornx.security.auth.dto.AcceptInviteRequest;
import com.example.vornx.security.auth.dto.AuthResponse;
import com.example.vornx.security.auth.dto.LoginChallengeResponse;
import com.example.vornx.security.auth.dto.LoginRequest;
import com.example.vornx.security.auth.dto.RefreshRequest;
import com.example.vornx.security.auth.dto.ResendCodeRequest;
import com.example.vornx.security.auth.dto.VerifyCodeRequest;
import com.example.vornx.security.auth.dto.VerifyPhoneRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.example.vornx.security.passkey.PasskeyService;
import com.example.vornx.security.session.ClientInfo;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints (no token needed): one login per portal, plus refresh and logout. */
@Tag(name = "Authentication", description = "Login for the 3 portals, customer sign-up, partner invitations, refresh, logout")
@SecurityRequirements // empty = these endpoints need no token (removes the lock icon in Swagger)
@RestController
@RequestMapping("/api/public/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasskeyService passkeyService;

    /** credential = JSON.stringify of what navigator.credentials.get() returned. */
    public record PasskeyLoginRequest(@jakarta.validation.constraints.NotBlank String challengeId,
                                      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20_000) String credential) {
    }

    /** Admin dashboard (index 55) */
    @PostMapping("/admin/login")
    public AuthResponse adminLogin(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.loginAdmin(req, ClientInfo.from(http));
    }

    /** Partner portal (hotel.html) */
    @PostMapping("/partner/login")
    public AuthResponse partnerLogin(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.loginPartnerUser(req, ClientInfo.from(http));
    }

    /** Invited VORNX team members (admin dashboard) open the email link and choose their password. */
    @PostMapping("/admin/accept-invite")
    public AuthResponse acceptAdminInvite(@Valid @RequestBody AcceptInviteRequest req, HttpServletRequest http) {
        return authService.acceptAdminInvite(req, ClientInfo.from(http));
    }

    /** Invited partner staff open the email link and choose their password. */
    @PostMapping("/partner/accept-invite")
    public AuthResponse acceptPartnerInvite(@Valid @RequestBody AcceptInviteRequest req, HttpServletRequest http) {
        return authService.acceptPartnerInvite(req, ClientInfo.from(http));
    }

    @Operation(summary = "Face ID / fingerprint sign-in, step 1: options for navigator.credentials.get()",
            description = "No email needed: the phone offers the VORNX passkeys it has. 429 when asked too often.")
    @PostMapping("/customer/passkey/options")
    public PasskeyService.Options customerPasskeyOptions(HttpServletRequest http) {
        return passkeyService.loginOptions(ClientInfo.from(http).ipAddress());
    }

    @Operation(summary = "Face ID / fingerprint sign-in, step 2: the signed challenge -> tokens",
            description = "409 PASSKEY_NOT_RECOGNIZED (e.g. removed in My account) / PASSKEY_CHALLENGE_INVALID.")
    @PostMapping("/customer/passkey/login")
    public AuthResponse customerPasskeyLogin(@Valid @RequestBody PasskeyLoginRequest req, HttpServletRequest http) {
        return authService.loginCustomerWithPasskey(req.challengeId(), req.credential(), ClientInfo.from(http));
    }

    /** Customer app (index_76), step 1. */
    @Operation(summary = "Customer login, step 1: email + password",
            description = "202 = a 6-digit code was emailed: send it to /customer/login/verify. "
                    + "200 = tokens straight away (only when two-step login is turned off).")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = LoginChallengeResponse.class)))
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @PostMapping("/customer/login")
    public ResponseEntity<?> customerLogin(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        AuthService.CustomerLogin result = authService.loginCustomer(req, ClientInfo.from(http));
        return result.challenge() != null
                ? ResponseEntity.status(HttpStatus.ACCEPTED).body(result.challenge())
                : ResponseEntity.ok(result.tokens());
    }

    @Operation(summary = "Customer login / sign-up, step 2: the code (email, authenticator app or recovery code)",
            description = "409 CODE_INVALID (with attemptsLeft), CODE_EXPIRED (ask for a new one), "
                    + "LOGIN_CHALLENGE_INVALID (start again with the password).")
    @PostMapping("/customer/login/verify")
    public AuthResponse customerVerifyCode(@Valid @RequestBody VerifyCodeRequest req, HttpServletRequest http) {
        return authService.verifyCustomerCode(req.challengeToken(), req.code(), ClientInfo.from(http));
    }

    @Operation(summary = "Customer login, step 2 by SMS: Firebase's ID token",
            description = "409 PHONE_VERIFICATION_FAILED / PHONE_MISMATCH (with attemptsLeft), "
                    + "LOGIN_CHALLENGE_INVALID (start again with the password).")
    @PostMapping("/customer/login/verify-phone")
    public AuthResponse customerVerifyPhone(@Valid @RequestBody VerifyPhoneRequest req, HttpServletRequest http) {
        return authService.verifyCustomerPhone(req.challengeToken(), req.firebaseIdToken(), ClientInfo.from(http));
    }

    @Operation(summary = "Send the login code again (an SMS login switches to email)",
            description = "Once per 60 s, 3 sends per login. 409 CODE_RESEND_TOO_SOON (with resendIn) / CODE_RESEND_LIMIT.")
    @PostMapping("/customer/login/resend")
    public LoginChallengeResponse customerResendCode(@Valid @RequestBody ResendCodeRequest req) {
        return authService.resendCustomerCode(req.challengeToken());
    }

    @Operation(summary = "Customer sign-up",
            description = "202 = account created and a 6-digit code emailed: send it to /customer/login/verify to "
                    + "confirm the email and log in. acceptTerms must be true.")
    @PostMapping("/customer/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LoginChallengeResponse customerRegister(@Valid @RequestBody CustomerRegisterRequest req, HttpServletRequest http) {
        return authService.registerCustomer(req, ClientInfo.from(http));
    }

    /**
     * Works for all 3 portals. Call it when a request returns 401 because the access token expired.
     * Returns a NEW refresh token too: replace the old one, it can never be used again.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return authService.refresh(req.refreshToken(), ClientInfo.from(http));
    }

    /** Ends this device's session. Always 204, even for an unknown token. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
    }
}
