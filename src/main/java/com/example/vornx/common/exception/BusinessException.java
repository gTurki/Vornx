package com.example.vornx.common.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Throw when a business rule is broken, e.g. "The owner cannot be a delegate".
 * Becomes HTTP 409 with a machine-readable code the frontend can translate (AR/EN).
 * Optional details are added to the JSON too, e.g. {"attemptsLeft": 2}.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public BusinessException(String code, String message) {
        this(code, message, Map.of());
    }

    public BusinessException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
