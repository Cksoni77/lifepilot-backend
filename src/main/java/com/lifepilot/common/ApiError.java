package com.lifepilot.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {
    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                status.getReasonPhrase(),
                message,
                path);
    }
}
