package com.example.vornx.security;

/** The 3 kinds of people who log in. Each one can only use its own /api/... area. */
public enum UserType {
    ADMIN,         // -> /api/admin/**
    PARTNER_USER,  // -> /api/partner/**
    CUSTOMER;      // -> /api/customer/**

    /** The authority stored in the token, e.g. "TYPE_ADMIN". */
    public String authority() {
        return "TYPE_" + name();
    }
}
