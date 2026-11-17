package com.example.vornx.security.totp;

import com.example.vornx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A one-time code that replaces the authenticator app once (lost or new phone). Only the hash is stored. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer_recovery_codes")
public class RecoveryCode extends BaseEntity {

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "code_hash", nullable = false, length = 64, updatable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    public RecoveryCode(Long customerId, String codeHash) {
        this.customerId = customerId;
        this.codeHash = codeHash;
    }

    public void use(Instant now) {
        usedAt = now;
    }
}
