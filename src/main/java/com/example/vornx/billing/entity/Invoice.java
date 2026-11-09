package com.example.vornx.billing.entity;

import com.example.vornx.billing.enums.InvoiceStatus;
import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.partner.entity.Partner;
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
import java.time.LocalDate;
import java.time.YearMonth;

/** One monthly bill from VORNX to a partner. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    /** "2026-11" */
    @Column(nullable = false, length = 7, columnDefinition = "CHAR(7)", updatable = false)
    private String period;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "issue_date", nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    public Invoice(String number, Contract contract, YearMonth period, LocalDate issueDate, LocalDate dueDate) {
        this.number = number;
        this.contract = contract;
        this.partner = contract.getPartner();
        this.period = period.toString();
        this.amount = contract.getMonthlyFee();
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.status = InvoiceStatus.DUE;
    }

    public InvoiceStatus effectiveStatus(LocalDate today) {
        if (status == InvoiceStatus.DUE && today.isAfter(dueDate)) {
            return InvoiceStatus.OVERDUE;
        }
        return status;
    }

    public void markPaid(Instant at) {
        this.status = InvoiceStatus.PAID;
        this.paidAt = at;
    }
}
