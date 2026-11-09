package com.example.vornx.security.session;

import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.security.JwtProperties;
import com.example.vornx.security.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Refresh tokens with ROTATION and REUSE DETECTION:
 *
 *   login            -> session + refresh token #1
 *   refresh with #1  -> #1 is marked used, #2 is returned
 *   refresh with #2  -> #3 ...
 *   refresh with #1 AGAIN -> #1 was already used: someone copied it (stolen?) -> the WHOLE session is revoked,
 *                            so both the thief and the real user are logged out on that device.
 *
 * Small exception: if the same token is used twice within a few seconds (two browser tabs refreshing at the
 * same moment), it is a normal race, not theft: the second request just fails, the session stays alive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    public static final String TOKEN_PREFIX = "rt_";
    private static final Duration REUSE_GRACE = Duration.ofSeconds(30);
    private static final String INVALID = "Invalid or expired refresh token";

    private final AuthSessionRepository sessionRepository;
    private final RefreshTokenRepository tokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public record Issued(AuthSession session, String refreshToken) {
    }

    /** Called at login. */
    public Issued start(UserType type, Long userId, ClientInfo client) {
        Instant now = Instant.now(clock);
        Instant expires = now.plus(jwtProperties.refreshExpiration());
        AuthSession session = sessionRepository.save(
                new AuthSession(type, userId, client.userAgent(), client.ipAddress(), now, expires));
        return new Issued(session, newToken(session, expires));
    }

    /**
     * Exchange a refresh token for a new one. noRollbackFor: when we detect reuse we REVOKE the session
     * and then throw - the revoke must be saved, not rolled back with the exception.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Issued rotate(String rawToken, ClientInfo client) {
        Instant now = Instant.now(clock);
        RefreshToken token = tokenRepository.lockByHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException(INVALID));
        AuthSession session = token.getSession();

        if (!session.isActive(now)) {
            throw new BadCredentialsException(INVALID);
        }
        if (token.getUsedAt() != null) {
            if (token.getUsedAt().plus(REUSE_GRACE).isAfter(now)) {
                throw new BadCredentialsException("Refresh token already used"); // harmless race, keep the session
            }
            session.revoke(now, "TOKEN_REUSE");
            log.warn("Refresh token reuse detected: session {} ({} {}) revoked", session.getId(),
                    session.getUserType(), session.getUserId());
            throw new BadCredentialsException("Session revoked for security reasons. Please log in again.");
        }
        if (!now.isBefore(token.getExpiresAt())) {
            throw new BadCredentialsException(INVALID);
        }

        token.markUsed(now);
        Instant expires = now.plus(jwtProperties.refreshExpiration());
        session.touch(now, expires, client.ipAddress()); // sliding: active users stay logged in
        return new Issued(session, newToken(session, expires));
    }

    /** Logout on this device. Unknown or already-revoked tokens are ignored (logout always "works"). */
    public void logout(String rawToken) {
        tokenRepository.findByHash(hash(rawToken))
                .ifPresent(t -> t.getSession().revoke(Instant.now(clock), "LOGOUT"));
    }

    public void revoke(AuthSession session, String reason) {
        session.revoke(Instant.now(clock), reason);
    }

    // ---------- "My devices" ----------

    @Transactional(readOnly = true)
    public List<SessionResponse> list(UserType type, Long userId, Long currentSessionId) {
        return sessionRepository.findActive(type, userId, Instant.now(clock)).stream()
                .map(s -> SessionResponse.from(s, currentSessionId))
                .toList();
    }

    public void revokeOne(UserType type, Long userId, Long sessionId) {
        AuthSession session = sessionRepository.findByIdAndUserTypeAndUserId(sessionId, type, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        session.revoke(Instant.now(clock), "REVOKED_BY_USER");
    }

    /** "Log out all devices". keepSessionId = the current device, or null to log out everywhere. */
    public int revokeAll(UserType type, Long userId, Long keepSessionId) {
        return sessionRepository.revokeAll(type, userId, keepSessionId, Instant.now(clock), "LOGOUT_ALL");
    }

    // ---------- helpers ----------

    private String newToken(AuthSession session, Instant expires) {
        String raw = TOKEN_PREFIX + SecureTokens.generate();
        tokenRepository.save(new RefreshToken(session, hash(raw), expires));
        return raw;
    }

    private static String hash(String raw) {
        if (raw == null || !raw.startsWith(TOKEN_PREFIX)) {
            throw new BadCredentialsException(INVALID);
        }
        return SecureTokens.sha256(raw);
    }
}
