package com.example.vornx.partner.dto;

import java.time.Instant;

/**
 * TEMPORARY: until we send emails, the invite token is returned here so you can test.
 * When email is set up, the token will only be sent in the email link and removed from this response.
 */
public record PartnerUserInviteResponse(
        PartnerUserResponse user,
        String inviteToken,
        Instant inviteExpiresAt
) {
}
