package com.example.vornx.security.otp;

import com.example.vornx.common.exception.BusinessException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Firebase ID tokens: only a fresh, Google-signed, phone sign-in for OUR project passes.
 * "Google" here is a key made in the test, so it runs offline.
 */
class FirebasePhoneVerifierTest {

    private static final String PROJECT = "vornx-app";
    private static final Instant NOW = Instant.now();
    private static KeyPair google;
    private static KeyPair attacker;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        google = gen.generateKeyPair();
        attacker = gen.generateKeyPair();
    }

    private static FirebasePhoneVerifier verifier() {
        return new FirebasePhoneVerifier(PROJECT,
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) google.getPublic()).build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A valid token, changed by `edit`. */
    private static String token(KeyPair key, Consumer<JWTClaimsSet.Builder> edit) throws Exception {
        JWTClaimsSet.Builder c = new JWTClaimsSet.Builder()
                .issuer("https://securetoken.google.com/" + PROJECT)
                .audience(PROJECT)
                .subject("firebase-uid-1")
                .issueTime(Date.from(NOW.minusSeconds(10)))
                .expirationTime(Date.from(NOW.plusSeconds(3600)))
                .claim("auth_time", NOW.minusSeconds(10).getEpochSecond())
                .claim("phone_number", "+966551112233")
                .claim("firebase", Map.of("sign_in_provider", "phone"));
        edit.accept(c);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), c.build());
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }

    private static void rejected(String token) {
        assertEquals("PHONE_VERIFICATION_FAILED",
                assertThrows(BusinessException.class, () -> verifier().verifiedPhone(token)).getCode());
    }

    @Test
    void validPhoneSignInPasses() throws Exception {
        assertEquals("+966551112233", verifier().verifiedPhone(token(google, c -> { })));
    }

    @Test
    void notSignedByGoogle() throws Exception {
        rejected(token(attacker, c -> { }));
    }

    @Test
    void anotherFirebaseProject() throws Exception {
        rejected(token(google, c -> c.audience("someone-elses-app")));
        rejected(token(google, c -> c.issuer("https://securetoken.google.com/someone-elses-app")));
    }

    @Test
    void expired() throws Exception {
        rejected(token(google, c -> c.expirationTime(Date.from(NOW.minusSeconds(120)))));
    }

    @Test
    void oldSignInCannotBeReplayed() throws Exception {
        rejected(token(google, c -> c.claim("auth_time", NOW.minusSeconds(6 * 60).getEpochSecond())));
    }

    @Test
    void notAPhoneSignIn() throws Exception {
        rejected(token(google, c -> c.claim("firebase", Map.of("sign_in_provider", "password"))));
        rejected(token(google, c -> c.claim("phone_number", null)));
    }

    @Test
    void garbage() {
        rejected("not-a-jwt");
    }

    @Test
    void notConfiguredWithoutProjectId() {
        FirebasePhoneVerifier none = new FirebasePhoneVerifier("", Clock.systemUTC());
        assertFalse(none.isConfigured());
        assertEquals("SMS_LOGIN_DISABLED",
                assertThrows(BusinessException.class, () -> none.verifiedPhone("x")).getCode());
    }
}
