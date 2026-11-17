package com.example.vornx.whatsapp;

/**
 * WhatsApp is down, or our UltraMsg instance is not connected / not paid. Becomes HTTP 503 WHATSAPP_UNAVAILABLE.
 * Not a BusinessException on purpose: the transaction rolls back, so a failed send is not counted as a send.
 */
public class WhatsAppUnavailableException extends RuntimeException {

    public WhatsAppUnavailableException(String message) {
        super(message);
    }

    public WhatsAppUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
