package com.example.vornx.security.auth.dto;

import com.example.vornx.security.otp.CodePurpose;
import com.example.vornx.security.otp.LoginCodeChannel;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * "A code is needed": returned (HTTP 202) by login and sign-up instead of tokens, and by the mobile check.
 * Next, by channel:
 *  - EMAIL:    POST /customer/login/verify with challengeToken + the code from the email.
 *  - TOTP:     the same, with the code from the authenticator app (or a recovery code). No resend.
 *  - SMS:      the app asks Firebase to text phoneNumber, then sends Firebase's ID token to
 *              POST /customer/login/verify-phone. "Send by email instead" = /resend.
 *  - WHATSAPP: (purpose PHONE) POST /api/customer/phone-verification/confirm with the code from WhatsApp.
 *  - purpose:        LOGIN, SIGNUP or PHONE
 *  - destination:    masked, e.g. "tr•••7@gmail.com" or "+966 5•••••233" (absent for TOTP)
 *  - phoneNumber:    SMS only, full E.164 number: Firebase needs it to send the SMS
 *  - codeExpiresIn:  seconds this code works
 *  - resendIn:       seconds before "send again" is allowed
 *  - resendsLeft / attemptsLeft: how many more sends / wrong tries before starting again
 *  - devCode:        DEVELOPMENT ONLY (vornx.dev.expose-tokens=true), never in production
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginChallengeResponse(
        String status,
        String challengeToken,
        LoginCodeChannel channel,
        CodePurpose purpose,
        String destination,
        String phoneNumber,
        long codeExpiresIn,
        long resendIn,
        int resendsLeft,
        int attemptsLeft,
        String devCode
) {
}
