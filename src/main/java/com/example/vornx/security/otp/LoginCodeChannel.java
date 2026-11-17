package com.example.vornx.security.otp;

/**
 * Where the login code goes.
 *  EMAIL: we create the code and email it (through the outbox).
 *  SMS:   Firebase creates the code and texts it from the customer app; we verify Firebase's signed proof.
 */
public enum LoginCodeChannel {
    EMAIL,
    SMS
}
