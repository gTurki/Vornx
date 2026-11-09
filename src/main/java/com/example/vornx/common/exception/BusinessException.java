package com.example.vornx.common.exception;

import lombok.Getter;

/**
 * Throw when a business rule is broken, e.g. "The owner cannot be a delegate".
 * Becomes HTTP 409 with a machine-readable code the frontend can translate (AR/EN).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
