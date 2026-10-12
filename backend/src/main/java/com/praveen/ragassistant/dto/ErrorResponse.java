package com.praveen.ragassistant.dto;

import java.time.LocalDateTime;

/**
 * Standard error response envelope returned by GlobalExceptionHandler.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
