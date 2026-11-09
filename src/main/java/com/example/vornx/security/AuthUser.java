package com.example.vornx.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * "Who is calling?" - read from the token on every request.
 * Use it in controllers:  AuthUser user = AuthUser.from(jwt);
 *
 * @param id        the id in admin_users / partner_users / customers (depends on type)
 * @param role      e.g. SUPER_ADMIN or FRONT_DESK (null for customers)
 * @param partnerId only for partner users: which organization they belong to
 * @param sessionId the login session (device) this token belongs to
 */
public record AuthUser(Long id, UserType type, String role, Long partnerId, Long sessionId) {

    public static AuthUser from(Jwt jwt) {
        Number partnerId = jwt.getClaim(TokenService.CLAIM_PARTNER_ID);
        Number sessionId = jwt.getClaim(TokenService.CLAIM_SESSION_ID);
        return new AuthUser(
                Long.valueOf(jwt.getSubject()),
                UserType.valueOf(jwt.getClaimAsString(TokenService.CLAIM_TYPE)),
                jwt.getClaimAsString(TokenService.CLAIM_ROLE),
                partnerId != null ? partnerId.longValue() : null,
                sessionId != null ? sessionId.longValue() : null
        );
    }
}
