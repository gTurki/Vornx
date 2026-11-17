package com.example.vornx.security.otp;

import com.example.vornx.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Checks the proof the customer app gets from Firebase after the customer typed the SMS code.
 *
 * Firebase sends the SMS and checks the code; it then gives the app an "ID token" (a JWT signed by Google).
 * We trust it only if ALL of these hold:
 *  - signed by Google's securetoken keys (downloaded from Google, cached)
 *  - issued for OUR Firebase project (iss + aud), not expired
 *  - the sign-in was by phone, less than 5 minutes ago (an old token can't be replayed later)
 * The caller then compares the phone number in it with the customer's own number.
 *
 * No Firebase Admin SDK or service-account key is needed: only the public project id.
 */
@Slf4j
@Component
public class FirebasePhoneVerifier {

    static final String GOOGLE_KEYS =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
    static final Duration MAX_SIGN_IN_AGE = Duration.ofMinutes(5);

    private final String projectId;
    private final JwtDecoder decoder;

    @Autowired
    public FirebasePhoneVerifier(@Value("${vornx.auth.firebase.project-id:}") String projectId, Clock clock) {
        this(projectId, StringUtils.hasText(projectId) ? NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build() : null, clock);
    }

    /** Tests pass a decoder that trusts a local key instead of Google's. */
    FirebasePhoneVerifier(String projectId, JwtDecoder decoder, Clock clock) {
        this.projectId = projectId == null ? "" : projectId.trim();
        this.decoder = decoder;
        if (decoder instanceof NimbusJwtDecoder nimbus) {
            JwtTimestampValidator expiry = new JwtTimestampValidator(Duration.ofSeconds(30));
            expiry.setClock(clock);
            nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    expiry,
                    new JwtIssuerValidator("https://securetoken.google.com/" + this.projectId),
                    claim(jwt -> jwt.getAudience() != null && jwt.getAudience().contains(this.projectId), "wrong audience"),
                    claim(jwt -> StringUtils.hasText(jwt.getSubject()), "no subject"),
                    claim(jwt -> "phone".equals(signInProvider(jwt)), "not a phone sign-in"),
                    claim(jwt -> recentSignIn(jwt, clock), "sign-in is too old")));
        }
    }

    public boolean isConfigured() {
        return decoder != null && !projectId.isEmpty();
    }

    /** @return the verified phone number, E.164 (e.g. "+966551112233") */
    public String verifiedPhone(String idToken) {
        if (!isConfigured()) {
            throw new BusinessException("SMS_LOGIN_DISABLED", "SMS login is not set up");
        }
        try {
            String phone = decoder.decode(idToken).getClaimAsString("phone_number");
            if (!StringUtils.hasText(phone)) {
                throw new JwtException("no phone_number");
            }
            return phone;
        } catch (JwtException e) {
            log.info("Firebase ID token rejected: {}", e.getMessage());
            throw new BusinessException("PHONE_VERIFICATION_FAILED", "Phone verification failed. Try again.");
        }
    }

    private static String signInProvider(Jwt jwt) {
        Object firebase = jwt.getClaims().get("firebase");
        return firebase instanceof Map<?, ?> m && m.get("sign_in_provider") instanceof String s ? s : null;
    }

    private static boolean recentSignIn(Jwt jwt, Clock clock) {
        Object authTime = jwt.getClaims().get("auth_time");
        if (!(authTime instanceof Number n)) {
            return false;
        }
        Instant signedIn = Instant.ofEpochSecond(n.longValue());
        Instant now = Instant.now(clock);
        return !signedIn.isBefore(now.minus(MAX_SIGN_IN_AGE)) && !signedIn.isAfter(now.plusSeconds(30));
    }

    private static OAuth2TokenValidator<Jwt> claim(java.util.function.Predicate<Jwt> test, String reason) {
        return jwt -> test.test(jwt) ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", reason, null));
    }
}
