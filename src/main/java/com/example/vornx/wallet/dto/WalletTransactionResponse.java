package com.example.vornx.wallet.dto;

import com.example.vornx.customer.entity.Customer;
import com.example.vornx.wallet.entity.WalletTransaction;
import com.example.vornx.wallet.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionResponse(
        String reference,
        TransactionType type,
        String direction,       // "IN" (وارد) or "OUT" (صادر)
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String counterpartyName, // other person in a transfer, shown as "Khalid A." for privacy
        String partnerName,
        Instant createdAt
) {
    public static WalletTransactionResponse from(WalletTransaction t) {
        String counterparty = null;
        if (t.getCounterpartyWallet() != null) {
            Customer c = t.getCounterpartyWallet().getCustomer();
            counterparty = c.getFirstName() + " " + c.getLastName().charAt(0) + ".";
        }
        return new WalletTransactionResponse(t.getReference(), t.getType(), t.getType().isCredit() ? "IN" : "OUT",
                t.getAmount(), t.getBalanceAfter(), t.getDescription(), counterparty,
                t.getPartner() != null ? t.getPartner().getName() : null, t.getCreatedAt());
    }
}
