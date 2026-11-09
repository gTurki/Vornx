package com.example.vornx.security.session;

import jakarta.servlet.http.HttpServletRequest;

/** Which device/browser and IP a login comes from (shown in "my devices"). */
public record ClientInfo(String userAgent, String ipAddress) {

    public static ClientInfo from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return new ClientInfo(request.getHeader("User-Agent"), ip);
    }
}
