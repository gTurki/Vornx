package com.example.vornx.security.otp;

/**
 * Where the code comes from.
 *  EMAIL:    we create the code and email it (through the outbox).
 *  SMS:      Firebase creates the code and texts it from the customer app; we verify Firebase's signed proof.
 *  TOTP:     the customer's authenticator app shows it; nothing is sent.
 *  WHATSAPP: we create the code and send it by WhatsApp (UltraMsg) - used to confirm the mobile number.
 */
public enum LoginCodeChannel {
    EMAIL,
    SMS,
    TOTP,
    WHATSAPP
}
