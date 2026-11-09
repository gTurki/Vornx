package com.example.vornx.security.auth.dto;

import com.example.vornx.security.UserType;

/**
 * Returned after login / register / refresh.
 *  - accessToken:  send on every request as "Authorization: Bearer <accessToken>". Expires in expiresIn seconds.
 *  - refreshToken: keep it safe; when the access token expires (401), call POST /api/public/auth/refresh with it.
 *                  You get a NEW refresh token each time - always replace the old one (it can't be used twice).
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        UserType userType,
        String role,
        Long sessionId
) {
}
