package com.example.vornx.security.device;

/**
 * "Which reader is calling?" for /api/devices/** requests (authenticated by API key, not JWT).
 * Use in controllers:  @AuthenticationPrincipal DevicePrincipal device
 */
public record DevicePrincipal(Long apiKeyId, Long partnerId) {

    public static final String AUTHORITY = "TYPE_DEVICE";
}
