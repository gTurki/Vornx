package com.example.vornx.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the short-lived ACCESS token (JWT).
 * The frontend sends it on every request:  Authorization: Bearer <token>
 * (Long-lived login is handled by refresh tokens, see SessionService.)
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    static final String CLAIM_TYPE = "type";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_PARTNER_ID = "partnerId";
    static final String CLAIM_SESSION_ID = "sid";
    static final String CLAIM_AUTHORITIES = "authorities";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public String createAccessToken(Long userId, UserType type, String role, Long partnerId, Long sessionId) {
        Instant now = Instant.now(clock);

        List<String> authorities = new ArrayList<>();
        authorities.add(type.authority());          // e.g. TYPE_ADMIN       -> which /api area
        if (role != null) {
            authorities.add("ROLE_" + role);        // e.g. ROLE_SUPER_ADMIN -> what they may do there
        }

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.expiration()))
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type.name())
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim(CLAIM_AUTHORITIES, authorities);
        if (role != null) {
            claims.claim(CLAIM_ROLE, role);
        }
        if (partnerId != null) {
            claims.claim(CLAIM_PARTNER_ID, partnerId);
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public long accessTokenSeconds() {
        return jwtProperties.expiration().toSeconds();
    }

    public long refreshTokenSeconds() {
        return jwtProperties.refreshExpiration().toSeconds();
    }
}
