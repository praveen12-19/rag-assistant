package com.praveen.ragassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * AI Configuration for Spring AI components:
 * - SimpleVectorStore with file persistence (loaded on startup, saved after document ingestion)
 * - Uses Google GenAI EmbeddingModel (text-embedding-004) configured via application.properties
 */
@Configuration
public class AiConfig {

    private static final Logger logger = LoggerFactory.getLogger(AiConfig.class);

    @Value("${rag.vector-store.file-path:data/vector-store.json}")
    private String vectorStorePath;

    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        // Initialize Spring AI's SimpleVectorStore with the Google GenAI embedding model
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        File file = new File(vectorStorePath);
        // On application startup, load existing embeddings if the vector store file exists
        if (file.exists() && file.length() > 2) {
            try {
                vectorStore.load(file);
                logger.info("Loaded persisted vector store successfully from: {}", file.getAbsolutePath());
            } catch (Exception e) {
                logger.warn("Could not load persisted vector store from {}: {}. Starting with fresh vector store.",
                        vectorStorePath, e.getMessage());
            }
        } else {
            logger.info("No existing vector store found at {}. A fresh vector store will be initialized.", vectorStorePath);
        }

        return vectorStore;
    }
}
