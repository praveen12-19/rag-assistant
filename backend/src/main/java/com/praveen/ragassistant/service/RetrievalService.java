package com.praveen.ragassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service handling vector retrieval from the SimpleVectorStore.
 * Queries the top-K chunks and extracts semantic similarity scores.
 */
@Service
public class RetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(RetrievalService.class);

    private final SimpleVectorStore vectorStore;

    @Value("${rag.top-k:3}")
    private int topK;

    @Value("${rag.similarity-threshold:0.65}")
    private double similarityThreshold;

    public RetrievalService(SimpleVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Performs similarity search for the user question against the vector store.
     * Fetches top-K candidates to evaluate their similarity score against the threshold.
     */
    public List<Document> retrieveTopK(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // Build the search request asking for top-K matching document chunks
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);
            logger.info("Vector retrieval for query '{}' returned {} chunks", query, results.size());
            return results;
        } catch (Exception e) {
            logger.error("Error executing vector similarity search for query '{}': {}", query, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getTopK() {
        return topK;
    }
}
