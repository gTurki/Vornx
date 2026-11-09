package com.example.vornx.common.util;

import java.security.SecureRandom;

/** Random human-readable codes for reference numbers (TX-..., SUB-..., REQ-...). */
public final class RandomCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** No 0/O/1/I, so codes are easy to read aloud. */
    private static final char[] ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private RandomCodes() {
    }

    public static String digits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static String alphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC[RANDOM.nextInt(ALPHANUMERIC.length)]);
        }
        return sb.toString();
    }
}
