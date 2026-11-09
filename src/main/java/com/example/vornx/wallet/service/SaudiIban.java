package com.example.vornx.wallet.service;

import java.math.BigInteger;

/**
 * Builds a VALID Saudi IBAN: "SA" + 2 check digits + 2-digit bank code + 18-digit account = 24 characters.
 * The check digits use the international ISO 13616 "mod 97" rule, so any IBAN validator accepts it.
 */
public final class SaudiIban {

    /** Placeholder bank code until VORNX gets a real one from its banking partner. */
    static final String BANK_CODE = "99";

    private SaudiIban() {
    }

    public static String fromAccountNumber(String account18) {
        if (account18 == null || !account18.matches("\\d{18}")) {
            throw new IllegalArgumentException("Account number must be 18 digits");
        }
        String bban = BANK_CODE + account18;
        // Move "SA00" to the end and turn letters into numbers (A=10 ... Z=35): S=28, A=10
        BigInteger number = new BigInteger(bban + "28" + "10" + "00");
        int check = 98 - number.mod(BigInteger.valueOf(97)).intValue();
        return "SA" + String.format("%02d", check) + bban;
    }

    /** Standard IBAN check: a valid IBAN gives remainder 1. */
    public static boolean isValid(String iban) {
        if (iban == null || !iban.matches("SA\\d{22}")) {
            return false;
        }
        String rearranged = iban.substring(4) + "2810" + iban.substring(2, 4);
        return new BigInteger(rearranged).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
