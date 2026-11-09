package com.example.vornx.wallet.dto;

import com.example.vornx.wallet.entity.Wallet;

import java.math.BigDecimal;

public record WalletResponse(String iban, BigDecimal balance, String currency) {

    public static WalletResponse from(Wallet w) {
        return new WalletResponse(w.getIban(), w.getBalance(), w.getCurrency());
    }
}
