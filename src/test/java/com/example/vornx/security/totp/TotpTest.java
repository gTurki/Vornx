package com.example.vornx.security.totp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The same codes as Google Authenticator & co: checked against the official RFC 6238 test values. */
class TotpTest {

    /** RFC 6238 appendix B, SHA1: the secret is the ASCII text "12345678901234567890". */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfcTestVectors() {
        assertEquals("94287082", Totp.code(RFC_KEY, 59 / 30, 8));
        assertEquals("07081804", Totp.code(RFC_KEY, 1111111109L / 30, 8));
        assertEquals("14050471", Totp.code(RFC_KEY, 1111111111L / 30, 8));
        assertEquals("89005924", Totp.code(RFC_KEY, 1234567890L / 30, 8));
        assertEquals("69279037", Totp.code(RFC_KEY, 2000000000L / 30, 8));
    }

    @Test
    void base32RoundTrip() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32Encode(RFC_KEY));
        assertArrayEquals(RFC_KEY, Totp.base32Decode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"));
        assertThrows(IllegalArgumentException.class, () -> Totp.base32Decode("not base32!"));
        String secret = Totp.newSecret();
        assertEquals(32, secret.length()); // 20 bytes
        assertNotEquals(secret, Totp.newSecret());
    }

    @Test
    void acceptsTheCurrentAndNeighbourStepsOnlyAndNeverTwice() {
        String secret = Totp.base32Encode(RFC_KEY);
        Instant now = Instant.ofEpochSecond(1234567890L);
        long step = Totp.step(now);
        String current = Totp.code(RFC_KEY, step, 6);

        assertEquals(step, Totp.match(secret, current, now, null));
        // phone clock 30 s behind / ahead
        assertEquals(step - 1, Totp.match(secret, Totp.code(RFC_KEY, step - 1, 6), now, null));
        assertEquals(step + 1, Totp.match(secret, Totp.code(RFC_KEY, step + 1, 6), now, null));
        // 2 minutes old: refused
        assertEquals(-1, Totp.match(secret, Totp.code(RFC_KEY, step - 4, 6), now, null));
        // already used (lastStep = step): the same code is refused, and so are older ones
        assertEquals(-1, Totp.match(secret, current, now, step));
        assertEquals(-1, Totp.match(secret, Totp.code(RFC_KEY, step - 1, 6), now, step));
        assertEquals(-1, Totp.match(secret, "12345", now, null));
        assertEquals(-1, Totp.match(secret, null, now, null));
    }

    @Test
    void buildsTheQrCodeUri() {
        String uri = Totp.uri("VORNX", "fahad+1@example.com", "ABC");
        assertEquals("otpauth://totp/VORNX:fahad%2B1%40example.com?secret=ABC&issuer=VORNX&algorithm=SHA1&digits=6&period=30", uri);
    }

    @Test
    void secretBoxEncryptsAndOnlyTheSameKeyDecrypts() {
        SecretBox box = new SecretBox(new byte[32]);
        String a = box.encrypt("JBSWY3DPEHPK3PXP");
        String b = box.encrypt("JBSWY3DPEHPK3PXP");
        assertNotEquals(a, b); // random IV: the same secret never looks the same
        assertTrue(!a.contains("JBSWY3DPEHPK3PXP"));
        assertEquals("JBSWY3DPEHPK3PXP", box.decrypt(a));

        byte[] other = new byte[32];
        other[0] = 1;
        assertThrows(IllegalStateException.class, () -> new SecretBox(other).decrypt(a));
        // tampered
        String tampered = a.substring(0, a.length() - 3) + (a.endsWith("A") ? "BBB" : "AAA");
        assertThrows(IllegalStateException.class, () -> box.decrypt(tampered));
    }
}
