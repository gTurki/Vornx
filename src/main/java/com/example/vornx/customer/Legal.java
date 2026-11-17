package com.example.vornx.customer;

/**
 * The version of the terms of use + privacy policy shown in the customer app (/terms, /privacy).
 * Each sign-up stores the version it accepted. When the texts change, change this date too
 * (frontend/customer/src/pages/LegalPages.tsx shows the same date).
 */
public final class Legal {

    public static final String TERMS_VERSION = "2026-11-17";

    private Legal() {
    }
}
