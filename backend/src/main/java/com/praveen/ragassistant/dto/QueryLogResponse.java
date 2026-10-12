package com.praveen.ragassistant.dto;

import java.time.LocalDateTime;

/**
 * Response DTO representing logged queries for admin audit views.
 */
public record QueryLogResponse(
        Long id,
        String question,
        String answer,
        Double topScore,
        Boolean answered,
        String citations,
        Long latencyMs,
        LocalDateTime createdAt
) {
}
