package com.example.vornx.platform.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.platform.enums.AdminRole;
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

/** A member of the VORNX team who uses the admin dashboard (index 55). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs it; our code uses the constructor below
@Entity
@Table(name = "admin_users")
public class AdminUser extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 20)
    private String phone;

    /** Null while INVITED: the person chooses it from the invite link. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    /** SHA-256 of the invite link's token (the raw token is only in the email). */
    @Column(name = "invite_token_hash", length = 64)
    private String inviteTokenHash;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public AdminUser(String fullName, String email, String passwordHash, AdminRole role) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = AccountStatus.ACTIVE;
    }

    /** "فريق VORNX": an INVITED admin with no password yet. They become ACTIVE when they accept. */
    public static AdminUser invite(String fullName, String email, AdminRole role, String inviteTokenHash, Instant expiresAt) {
        AdminUser u = new AdminUser();
        u.fullName = fullName;
        u.email = email;
        u.role = role;
        u.status = AccountStatus.INVITED;
        u.inviteTokenHash = inviteTokenHash;
        u.inviteExpiresAt = expiresAt;
        return u;
    }

    /** A new link (the old one stops working). */
    public void reissueInvite(String inviteTokenHash, Instant expiresAt) {
        this.inviteTokenHash = inviteTokenHash;
        this.inviteExpiresAt = expiresAt;
    }

    public void acceptInvite(String passwordHash) {
        this.passwordHash = passwordHash;
        this.status = AccountStatus.ACTIVE;
        this.inviteTokenHash = null; // a link works only once
        this.inviteExpiresAt = null;
    }

    /** Cancelling an invite or suspending: any pending link stops working. */
    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
        this.inviteTokenHash = null;
        this.inviteExpiresAt = null;
    }
}
