package com.example.vornx.wallet.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.customer.entity.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The customer's VORNX wallet. Notice: NO setter for balance.
 * The only way to change it is credit()/debit(), which enforce the rules.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "wallets")
public class Wallet extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(nullable = false, length = 24, updatable = false)
    private String iban;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    public Wallet(Customer customer, String iban) {
        this.customer = customer;
        this.iban = iban;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.currency = "SAR";
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException("INSUFFICIENT_FUNDS", "Your wallet balance is not enough");
        }
        balance = balance.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
