package com.example.vornx.security.otp;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.security.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Step 2 of a login: "we sent you a code, type it here". One row per login attempt. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "login_challenges")
public class LoginChallenge extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20, updatable = false)
    private UserType userType;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private LoginCodeChannel channel;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "code_expires_at", nullable = false)
    private Instant codeExpiresAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private int sends;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    public LoginChallenge(UserType userType, Long userId, LoginCodeChannel channel, String tokenHash, String codeHash,
                          Instant now, Instant codeExpiresAt, Instant expiresAt, String ipAddress) {
        this.userType = userType;
        this.userId = userId;
        this.channel = channel;
        this.tokenHash = tokenHash;
        this.codeHash = codeHash;
        this.codeExpiresAt = codeExpiresAt;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.sends = 1;
        this.lastSentAt = now;
        this.ipAddress = ipAddress;
    }

    /** Still open: not used, not burned, not past its 15 minutes. */
    public boolean isOpen(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public boolean isCodeExpired(Instant now) {
        return !now.isBefore(codeExpiresAt);
    }

    /** One more wrong code. Returns how many tries are left (0 = the challenge is now burned). */
    public int failAttempt(Instant now, int maxAttempts) {
        attempts++;
        int left = Math.max(0, maxAttempts - attempts);
        if (left == 0) {
            consume(now);
        }
        return left;
    }

    /** A fresh code replaces the old one (the old code stops working). Wrong-code tries carry over. */
    public void resend(String newCodeHash, Instant now, Instant newCodeExpiresAt) {
        this.codeHash = newCodeHash;
        this.codeExpiresAt = newCodeExpiresAt;
        this.sends++;
        this.lastSentAt = now;
    }

    public void consume(Instant now) {
        if (consumedAt == null) {
            consumedAt = now;
        }
    }
}
