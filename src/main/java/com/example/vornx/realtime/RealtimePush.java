package com.example.vornx.realtime;

/**
 * "Tell everyone listening on this channel that something happened."
 *
 * Any module publishes this as a Spring event INSIDE its transaction. RealtimeRelay sends it only
 * AFTER the commit - so the browser never hears about something that was rolled back, and when it
 * re-loads the data, the data is already there.
 *
 * Channels:
 *   CUSTOMER:{customerId}  one customer (all their devices)
 *   PARTNER:{partnerId}    the whole team of one partner
 *   ADMINS                 every VORNX admin
 */
public record RealtimePush(String channel, String type, Object data) {

    public static String customer(Long id) {
        return "CUSTOMER:" + id;
    }

    public static String partner(Long id) {
        return "PARTNER:" + id;
    }

    public static final String ADMINS = "ADMINS";
}
