package com.example.vornx.subscription.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.subscription.enums.RequestStatus;
import com.example.vornx.subscription.enums.RequestType;
import com.example.vornx.wallet.entity.WalletTransaction;
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
import java.time.Instant;

/** "طلب تفعيل" / "طلب تجديد". The customer has already paid; the partner decides. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "service_requests")
public class ServiceRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, length = 20, updatable = false)
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false, updatable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id", updatable = false)
    private WalletTransaction paymentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_transaction_id")
    private WalletTransaction refundTransaction;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by_partner_user_id")
    private Long decidedByPartnerUserId;

    public ServiceRequest(String requestNumber, Subscription subscription, RequestType type, BigDecimal amount,
                          WalletTransaction paymentTransaction) {
        this.requestNumber = requestNumber;
        this.subscription = subscription;
        this.customer = subscription.getCustomer();
        this.partner = subscription.getPartner();
        this.type = type;
        this.amount = amount;
        this.paymentTransaction = paymentTransaction;
        this.status = RequestStatus.PENDING;
    }

    public void approve(Instant at, Long partnerUserId) {
        this.status = RequestStatus.APPROVED;
        this.decidedAt = at;
        this.decidedByPartnerUserId = partnerUserId;
    }

    public void reject(String reason, Instant at, Long partnerUserId, WalletTransaction refund) {
        this.status = RequestStatus.REJECTED;
        this.rejectionReason = reason;
        this.decidedAt = at;
        this.decidedByPartnerUserId = partnerUserId;
        this.refundTransaction = refund;
    }

    public void cancel(Instant at, WalletTransaction refund) {
        this.status = RequestStatus.CANCELLED;
        this.decidedAt = at;
        this.refundTransaction = refund;
    }
}
