package com.example.vornx.wallet.enums;

public enum TransactionType {
    TOP_UP(true),
    TRANSFER_IN(true),
    TRANSFER_OUT(false),
    PAYMENT(false),
    REFUND(true),
    CASHBACK(true);

    /** true = money comes IN to the wallet ("وارد"), false = goes OUT ("صادر"). */
    private final boolean credit;

    TransactionType(boolean credit) {
        this.credit = credit;
    }

    public boolean isCredit() {
        return credit;
    }
}
