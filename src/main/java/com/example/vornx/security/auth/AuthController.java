package com.example.vornx.security.auth;

import com.example.vornx.customer.dto.CustomerRegisterRequest;
import com.example.vornx.security.auth.dto.AcceptInviteRequest;
import com.example.vornx.security.auth.dto.AuthResponse;
import com.example.vornx.security.auth.dto.LoginRequest;
import com.example.vornx.security.auth.dto.RefreshRequest;
import com.example.vornx.security.session.ClientInfo;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    /** Invited partner staff open the email link and choose their password. */
    @PostMapping("/partner/accept-invite")
    public AuthResponse acceptPartnerInvite(@Valid @RequestBody AcceptInviteRequest req, HttpServletRequest http) {
        return authService.acceptPartnerInvite(req, ClientInfo.from(http));
    }

    /** Customer app (index_76) */
    @PostMapping("/customer/login")
    public AuthResponse customerLogin(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.loginCustomer(req, ClientInfo.from(http));
    }

    @PostMapping("/customer/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse customerRegister(@Valid @RequestBody CustomerRegisterRequest req, HttpServletRequest http) {
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
