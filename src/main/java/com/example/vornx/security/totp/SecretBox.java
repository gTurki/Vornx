package com.example.vornx.security.totp;

import com.example.vornx.security.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the authenticator secrets before they go into the database (AES-256-GCM).
 * Unlike passwords these can't be hashed: we need the secret itself to compute the expected code.
 * A leaked database alone therefore doesn't let anyone create codes.
 *
 * Key: TOTP_ENCRYPTION_KEY (base64 of 32 random bytes). If it is not set, a key is derived from JWT_SECRET.
 * Changing the key later makes the saved secrets unreadable (customers would have to set the app up again),
 * so choose it once.
 */
@Slf4j
@Component
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    @Autowired
    public SecretBox(@Value("${vornx.auth.totp-encryption-key:}") String keyBase64, JwtProperties jwt) {
        this(StringUtils.hasText(keyBase64) ? Base64.getDecoder().decode(keyBase64.trim()) : derive(jwt.secret()));
        if (!StringUtils.hasText(keyBase64)) {
            log.info("TOTP_ENCRYPTION_KEY not set: the authenticator secrets are encrypted with a key derived from JWT_SECRET");
        }
    }

    SecretBox(byte[] rawKey) {
        if (rawKey.length != 32) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY must be 32 bytes (base64)");
        }
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Unknown secret format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // wrong key (TOTP_ENCRYPTION_KEY changed) or tampered value
            throw new IllegalStateException("The authenticator secret can't be decrypted", e);
        }
    }

    private static byte[] derive(String jwtSecret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("vornx-totp-key:" + jwtSecret).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
