package com.example.vornx.security.otp;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.customer.dto.CustomerResponse;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.security.AuthUser;
import com.example.vornx.security.auth.dto.LoginChallengeResponse;
import com.example.vornx.security.auth.dto.ResendCodeRequest;
import com.example.vornx.security.auth.dto.VerifyCodeRequest;
import com.example.vornx.security.session.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in customer confirms their mobile number with a code sent by WhatsApp. */
@Tag(name = "Customer - Mobile verification", description = "Confirm the mobile number by WhatsApp")
@RestController
@RequestMapping("/api/customer/phone-verification")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final Flow flow;

    @Operation(summary = "Send a code by WhatsApp",
            description = "409 PHONE_ALREADY_VERIFIED / PHONE_VERIFICATION_OFF; 429 too many; 503 WHATSAPP_UNAVAILABLE.")
    @PostMapping
    public LoginChallengeResponse start(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        return flow.start(AuthUser.from(jwt).id(), ClientInfo.from(http).ipAddress());
    }

    @Operation(summary = "Send the WhatsApp code again", description = "Once per 60 s, 3 sends.")
    @PostMapping("/resend")
    public LoginChallengeResponse resend(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ResendCodeRequest req) {
        return flow.resend(req.challengeToken(), AuthUser.from(jwt).id());
    }

    @Operation(summary = "Confirm with the code from WhatsApp", description = "Returns the updated profile.")
    @PostMapping("/confirm")
    public CustomerResponse confirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VerifyCodeRequest req) {
        return flow.confirm(req.challengeToken(), req.code(), AuthUser.from(jwt).id());
    }

    /** The transactions around LoginCodeService (which always runs inside one). */
    @Service
    @RequiredArgsConstructor
    static class Flow {

        private final LoginCodeService codes;
        private final CustomerService customers;

        // A WhatsApp failure (not a BusinessException) rolls back: the failed send isn't counted.
        @Transactional(noRollbackFor = BusinessException.class)
        public LoginChallengeResponse start(Long customerId, String ip) {
            return codes.startPhone(customerId, ip);
        }

        @Transactional(noRollbackFor = BusinessException.class)
        public LoginChallengeResponse resend(String token, Long customerId) {
            return codes.resendPhone(token, customerId);
        }

        /** noRollbackFor: a wrong code is counted and SAVED. */
        @Transactional(noRollbackFor = BusinessException.class)
        public CustomerResponse confirm(String token, String code, Long customerId) {
            return customers.toResponse(codes.verifyPhoneCode(token, code, customerId));
        }
    }
}
