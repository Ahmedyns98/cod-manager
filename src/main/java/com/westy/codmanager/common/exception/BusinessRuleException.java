package com.westy.codmanager.common.exception;

/**
 * Thrown when a request is well formed but violates a domain rule,
 * for example an illegal order status transition. Maps to HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
