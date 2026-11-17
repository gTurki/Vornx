package com.example.vornx.whatsapp;

/** Sends one WhatsApp text message. Throws WhatsAppUnavailableException when it could not be sent. */
public interface WhatsAppSender {

    /** @param toE164 the number in international format, e.g. +966551112233 */
    void send(String toE164, String text);

    /** False for the development sender that only writes to the log. */
    boolean isReal();
}
