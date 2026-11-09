package com.example.vornx.wallet.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.wallet.enums.TransactionType;
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

import java.math.BigDecimal;

/** One money movement. Immutable: no setters, every column updatable = false. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(length = 255, updatable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_wallet_id", updatable = false)
    private Wallet counterpartyWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", updatable = false)
    private Partner partner;

    public WalletTransaction(String reference, Wallet wallet, TransactionType type, BigDecimal amount,
                             String description, Wallet counterpartyWallet, Partner partner) {
        this.reference = reference;
        this.wallet = wallet;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = wallet.getBalance(); // call AFTER credit()/debit()
        this.description = description;
        this.counterpartyWallet = counterpartyWallet;
        this.partner = partner;
    }
}
