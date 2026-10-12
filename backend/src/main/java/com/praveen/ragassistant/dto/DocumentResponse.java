package com.praveen.ragassistant.dto;

import java.time.LocalDateTime;

/**
 * Response DTO representing uploaded document metadata returned to clients.
 */
public record DocumentResponse(
        Long id,
        String fileName,
        Integer version,
        Integer pageCount,
        Integer chunkCount,
        LocalDateTime uploadedAt
) {
}
