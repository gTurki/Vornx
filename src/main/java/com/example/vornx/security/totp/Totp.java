package com.example.vornx.security.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Time-based one-time codes (RFC 6238), the standard every authenticator app uses
 * (Google Authenticator, Microsoft Authenticator, Authy, 1Password...).
 *
 * The app and the server share a random secret. Every 30 seconds both compute
 * HMAC-SHA1(secret, number of 30-second steps since 1970) and keep 6 digits. Same secret + same time = same code,
 * without any network or SMS.
 */
public final class Totp {

    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;
    /** Accept the previous and the next step too: phones' clocks are often a few seconds off. */
    static final int WINDOW = 1;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {
    }

    /** 160 random bits (the size RFC 4226 recommends), as Base32 - what the user's app stores. */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
    }

    /**
     * Checks a typed code. Returns the matched step, or -1.
     * Steps at or before lastStep are refused: a code that was already used (or an older one) never works again.
     */
    public static long match(String base32Secret, String code, Instant now, Long lastStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        byte[] key = base32Decode(base32Secret);
        long current = step(now);
        for (long s = current - WINDOW; s <= current + WINDOW; s++) {
            if (lastStep != null && s <= lastStep) {
                continue;
            }
            // constant-time compare
            if (MessageDigest.isEqual(code(key, s, DIGITS).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return s;
            }
        }
        return -1;
    }

    /** The QR code content: otpauth://totp/VORNX:email?secret=...&issuer=VORNX */
    public static String uri(String issuer, String account, String base32Secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** RFC 4226 HOTP: HMAC-SHA1 of the counter, "dynamic truncation", last N digits. */
    static String code(byte[] key, long counter, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = h[h.length - 1] & 0x0f;
            int binary = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16)
                    | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
            int mod = (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", binary % mod);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 not available", e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 31]);
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        String s = text.replace("=", "").replace(" ", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : s.toCharArray()) {
            int v = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
            if (v < 0) {
                throw new IllegalArgumentException("Not Base32");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
