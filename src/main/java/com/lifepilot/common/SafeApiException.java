package com.lifepilot.common;

import org.springframework.http.HttpStatus;

public class SafeApiException extends RuntimeException {
    private final HttpStatus status;

    public SafeApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
