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
}
