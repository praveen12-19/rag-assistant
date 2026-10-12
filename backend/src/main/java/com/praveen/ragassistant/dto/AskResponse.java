package com.praveen.ragassistant.dto;

import java.util.List;

/**
 * Response payload returned when querying the Verified RAG Knowledge Assistant.
 * Schema: { answered, answer, confidence, citations:[{document, page, snippet}] }
 */
public record AskResponse(
        boolean answered,
        String answer,
        double confidence,
        List<Citation> citations
) {
}
