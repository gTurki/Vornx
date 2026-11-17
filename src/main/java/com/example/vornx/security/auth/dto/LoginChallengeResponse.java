package com.example.vornx.security.auth.dto;

import com.example.vornx.security.otp.LoginCodeChannel;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Returned (HTTP 202) instead of tokens when the password was right but a code is still needed.
 * Next, by channel:
 *  - EMAIL: POST /customer/login/verify with challengeToken + the code from the email.
 *  - SMS:   the app asks Firebase to text phoneNumber, the customer types the code into Firebase, and the app
 *           sends Firebase's ID token to POST /customer/login/verify-phone. "Send by email instead" = /resend.
 *  - destination:    masked, e.g. "tr•••7@gmail.com" or "+966 5•••••233" (show "we sent a code to ...")
 *  - phoneNumber:    SMS only, full E.164 number: Firebase needs it to send the SMS
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
        String phoneNumber,
        long codeExpiresIn,
        long resendIn,
        int resendsLeft,
        int attemptsLeft,
        String devCode
) {
}
