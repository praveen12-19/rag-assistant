package com.praveen.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for asking a question to the Verified RAG Knowledge Assistant.
 */
public record AskRequest(
        @NotBlank(message = "Question must not be blank")
        @Size(max = 1000, message = "Question must not exceed 1000 characters")
        String question
) {
}
