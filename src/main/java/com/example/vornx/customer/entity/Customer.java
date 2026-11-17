package com.example.vornx.customer.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.enums.Language;
import com.example.vornx.customer.enums.LoyaltyTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** The end user who holds the VORNX card and uses the customer app (index_76). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    /** Public id shown to the customer, e.g. VX4821930576 (we never expose the DB id). */
    @Column(name = "vornx_id", nullable = false, length = 20, updatable = false)
    private String vornxId;

    /** Filled later during identity verification. */
    @Column(name = "national_id", length = 20)
    private String nationalId;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(nullable = false)
    private int points;

    /** The customer typed a code we emailed (sign-up, a login by email, or a password reset). */
    @Setter(AccessLevel.NONE)
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** The customer typed the code we sent by WhatsApp to this number. */
    @Setter(AccessLevel.NONE)
    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    /** Version of the terms + privacy policy accepted at sign-up (e.g. "2026-11-17"), and when. */
    @Setter(AccessLevel.NONE)
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Setter(AccessLevel.NONE)
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    /** Authenticator app secret, ENCRYPTED (see SecretBox). Null = not turned on. */
    @Column(name = "totp_secret")
    private String totpSecret;

    /** Scanned but not yet confirmed with a code. */
    @Column(name = "totp_pending_secret")
    private String totpPendingSecret;

    @Column(name = "totp_enabled_at")
    private Instant totpEnabledAt;

    /** 30-second step of the last accepted code: a code works once. */
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    public Customer(String vornxId, String firstName, String lastName, String phone, String email,
                    String passwordHash, Language language) {
        this.vornxId = vornxId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.email = email;
        this.passwordHash = passwordHash;
        this.language = language;
        this.status = AccountStatus.ACTIVE;
        this.points = 0;
    }

    public LoyaltyTier getTier() {
        return LoyaltyTier.fromPoints(points);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }

    public boolean isTotpEnabled() {
        return totpEnabledAt != null && totpSecret != null;
    }

    /** Returns true the first time (so the caller can send the welcome email once). */
    public boolean markEmailVerified(Instant now) {
        if (emailVerifiedAt != null) {
            return false;
        }
        emailVerifiedAt = now;
        return true;
    }

    public void markPhoneVerified(Instant now) {
        phoneVerifiedAt = now;
    }

    public void acceptTerms(String version, Instant now) {
        termsVersion = version;
        termsAcceptedAt = now;
    }
}
