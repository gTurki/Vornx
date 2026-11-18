package com.example.vornx.security.passkey;

import com.example.vornx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** The random challenge the phone must sign, kept between "options" and "finish". Works once, for 5 minutes. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "passkey_challenges")
public class PasskeyChallenge extends BaseEntity {

    public enum Purpose { REGISTER, LOGIN }

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Purpose purpose;

    /** REGISTER only: who is adding the passkey. */
    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    /** The library's request (contains the challenge), to finish with exactly what was asked. */
    @Column(name = "request_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "ip_address", updatable = false, length = 64)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public PasskeyChallenge(String tokenHash, Purpose purpose, Long customerId, String requestJson, String ipAddress,
                            Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.customerId = customerId;
        this.requestJson = requestJson;
        this.ipAddress = ipAddress;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void use(Instant now) {
        this.usedAt = now;
    }
}
