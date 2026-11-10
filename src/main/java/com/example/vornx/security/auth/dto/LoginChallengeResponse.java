package com.example.vornx.security.auth.dto;

import com.example.vornx.security.otp.LoginCodeChannel;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Returned (HTTP 202) instead of tokens when the password was right but a code is still needed.
 * Next: POST /customer/login/verify with challengeToken + the code from the email.
 *  - destination:    masked, e.g. "tr•••7@gmail.com" (show "we sent a code to ...")
 *  - codeExpiresIn:  seconds this code works
 *  - resendIn:       seconds before "send again" is allowed
 *  - resendsLeft / attemptsLeft: how many more sends / wrong tries before logging in again
 *  - devCode:        DEVELOPMENT ONLY (vornx.dev.expose-tokens=true), never in production
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginChallengeResponse(
        String status,
        String challengeToken,
        LoginCodeChannel channel,
        String destination,
        long codeExpiresIn,
        long resendIn,
        int resendsLeft,
        int attemptsLeft,
        String devCode
) {
}
