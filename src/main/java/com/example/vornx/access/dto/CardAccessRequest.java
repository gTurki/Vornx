package com.example.vornx.access.dto;

import com.example.vornx.access.enums.AccessMethod;
import com.example.vornx.access.enums.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Sent by a reader when someone taps:
 * POST /api/devices/access-decisions   (header X-API-Key: vx_...)
 * {"accessPointId": 1, "cardNumber": "9123456789012345", "direction": "ENTRY"}
 */
public record CardAccessRequest(
        @NotNull Long accessPointId,
        @NotBlank String cardNumber,
        Direction direction, // default ENTRY
        AccessMethod method  // default CARD
) {
}
