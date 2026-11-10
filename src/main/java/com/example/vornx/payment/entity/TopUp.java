package com.example.vornx.payment.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.payment.enums.TopUpStatus;
import com.example.vornx.wallet.entity.WalletTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** One attempt to add money to a wallet by card. Changes state only through the methods below. */
@Getter
@Entity
@Table(name = "wallet_top_ups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopUp extends BaseEntity {

    public static final String PROVIDER_MOYASAR = "MOYASAR";

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(name = "provider_invoice_id", unique = true, length = 64)
    private String providerInvoiceId;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TopUpStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_transaction_id", unique = true)
    private WalletTransaction walletTransaction;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    public TopUp(String reference, Customer customer, BigDecimal amount, String currency, String provider, Instant expiresAt) {
        this.reference = reference;
        this.customer = customer;
        this.amount = amount;
        this.currency = currency;
        this.provider = provider;
        this.expiresAt = expiresAt;
        this.status = TopUpStatus.PENDING;
    }

    public void attachInvoice(String invoiceId, String checkoutUrl) {
        this.providerInvoiceId = invoiceId;
        this.checkoutUrl = checkoutUrl;
    }

    public void checked(Instant now) {
        this.lastCheckedAt = now;
    }

    public void markPaid(WalletTransaction tx, Instant now) {
        requirePending();
        this.status = TopUpStatus.PAID;
        this.walletTransaction = tx;
        this.paidAt = now;
    }

    public void markFailed(String reason) {
        requirePending();
        this.status = TopUpStatus.FAILED;
        this.failureReason = reason.length() > 255 ? reason.substring(0, 255) : reason;
    }

    public void markExpired() {
        requirePending();
        this.status = TopUpStatus.EXPIRED;
    }

    public boolean isPending() {
        return status == TopUpStatus.PENDING;
    }

    private void requirePending() {
        if (status != TopUpStatus.PENDING) {
            throw new IllegalStateException("Top-up " + reference + " is already " + status);
        }
    }
}
