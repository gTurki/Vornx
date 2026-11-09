package com.example.vornx.email;

/** Kept on each outbox row, for reporting ("how many invites failed this week?"). */
public enum EmailType {
    PARTNER_INVITE,
    CUSTOMER_WELCOME,
    PASSWORD_RESET,
    PASSWORD_CHANGED,
    INVOICE_ISSUED
}
