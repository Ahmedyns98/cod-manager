package com.westy.codmanager.common.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object identifier) {
        super("%s not found: %s".formatted(resource, identifier));
    }

    public NotFoundException(String message) {
        super(message);
    }
}
