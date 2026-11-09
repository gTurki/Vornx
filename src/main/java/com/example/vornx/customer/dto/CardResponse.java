package com.example.vornx.customer.dto;

import com.example.vornx.customer.entity.Card;
import com.example.vornx.customer.enums.CardStatus;

import java.time.LocalDate;

public record CardResponse(
        Long id,
        String cardNumber,
        String last4,
        LocalDate issuedAt,
        LocalDate expiresAt,
        CardStatus status
) {
    public static CardResponse from(Card c) {
        String n = c.getCardNumber();
        return new CardResponse(c.getId(), n, n.substring(n.length() - 4), c.getIssuedAt(), c.getExpiresAt(),
                c.getStatus());
    }
}
