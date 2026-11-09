package com.example.vornx.customer.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.customer.enums.CardStatus;
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

import java.time.LocalDate;

/** The physical VORNX card ("ONE CARD"). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "cards")
public class Card extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(name = "card_number", nullable = false, length = 19, updatable = false)
    private String cardNumber;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status;

    public Card(Customer customer, String cardNumber, LocalDate issuedAt, LocalDate expiresAt) {
        this.customer = customer;
        this.cardNumber = cardNumber;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.status = CardStatus.ACTIVE;
    }

    /** Can this card be used to open anything today? */
    public boolean isUsable(LocalDate today) {
        return status == CardStatus.ACTIVE && !today.isAfter(expiresAt);
    }
}
