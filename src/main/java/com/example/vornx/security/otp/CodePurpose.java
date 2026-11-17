package com.example.vornx.security.otp;

/**
 * Why a code was sent. A code only works for its own purpose: a "confirm your mobile" code can never log anyone in.
 *  LOGIN:  second step of a login
 *  SIGNUP: confirm the email right after sign-up (also logs in)
 *  PHONE:  a logged-in customer confirms their mobile number by WhatsApp
 */
public enum CodePurpose {
    LOGIN,
    SIGNUP,
    PHONE
}
