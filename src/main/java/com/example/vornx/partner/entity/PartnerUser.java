package com.example.vornx.partner.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.partner.enums.PartnerRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A staff member of a partner who uses the partner portal (hotel.html). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "partner_users")
public class PartnerUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 120)
    private String email;

    /** Null while the person is INVITED and has not chosen a password yet. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PartnerRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "invite_token_hash", length = 64)
    private String inviteTokenHash;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Creates an INVITED user. They become ACTIVE when they accept and set a password. */
    public static PartnerUser invite(Partner partner, String fullName, String email, PartnerRole role,
                                     String inviteTokenHash, Instant inviteExpiresAt) {
        PartnerUser u = new PartnerUser();
        u.partner = partner;
        u.fullName = fullName;
        u.email = email;
        u.role = role;
        u.status = AccountStatus.INVITED;
        u.inviteTokenHash = inviteTokenHash;
        u.inviteExpiresAt = inviteExpiresAt;
        return u;
    }

    public void acceptInvite(String passwordHash) {
        this.passwordHash = passwordHash;
        this.status = AccountStatus.ACTIVE;
        this.inviteTokenHash = null;   // a token can be used only once
        this.inviteExpiresAt = null;
    }
}
