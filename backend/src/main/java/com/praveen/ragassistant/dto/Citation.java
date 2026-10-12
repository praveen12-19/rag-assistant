package com.praveen.ragassistant.dto;

/**
 * Citation DTO representing a referenced source document, page, and chunk text snippet.
 */
public record Citation(
        String document,
        Integer page,
        String snippet
) {
}
