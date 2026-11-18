package com.example.vornx.security.passkey;

import com.example.vornx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One passkey = one device (or one iCloud / Google account that syncs it) that can sign in with Face ID or a fingerprint.
 * Only the PUBLIC key is stored: the private key never leaves the phone.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "passkeys")
public class Passkey extends BaseEntity {

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    /** base64url */
    @Column(name = "credential_id", nullable = false, updatable = false, length = 512)
    private String credentialId;

    /** base64url. The same for all of a customer's passkeys; random, so it reveals nothing about them. */
    @Column(name = "user_handle", nullable = false, updatable = false, length = 128)
    private String userHandle;

    @Column(name = "public_key_cose", nullable = false, updatable = false)
    private byte[] publicKeyCose;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "backed_up", nullable = false)
    private boolean backedUp;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public Passkey(Long customerId, String credentialId, String userHandle, byte[] publicKeyCose, long signCount,
                   String name, boolean backedUp) {
        this.customerId = customerId;
        this.credentialId = credentialId;
        this.userHandle = userHandle;
        this.publicKeyCose = publicKeyCose;
        this.signCount = signCount;
        this.name = name;
        this.backedUp = backedUp;
    }

    public void used(long newSignCount, boolean backedUp, Instant at) {
        this.signCount = newSignCount;
        this.backedUp = backedUp;
        this.lastUsedAt = at;
    }
}
