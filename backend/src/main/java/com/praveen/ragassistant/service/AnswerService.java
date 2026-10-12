package com.praveen.ragassistant.service;

import com.praveen.ragassistant.dto.AskResponse;
import com.praveen.ragassistant.dto.Citation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating the Question-Answering pipeline:
 * 1. Queries RetrievalService for top-K vector matches with similarity scores.
 * 2. Evaluates the top score against the similarity threshold.
 *    - If below threshold: REFUSES IMMEDIATELY without calling the LLM (answered=false).
 *    - If above threshold: Calls Gemini with the grounded system prompt and returns citations.
 * 3. Logs every query, answer, confidence score, and latency to MySQL for auditability.
 */
@Service
public class AnswerService {

    private static final Logger logger = LoggerFactory.getLogger(AnswerService.class);
    public static final String FALLBACK_MESSAGE = "I couldn't find this in the uploaded documents.";

    private final RetrievalService retrievalService;
    private final ChatModel chatModel;
    private final QueryLogService queryLogService;

    @Value("classpath:prompts/rag-system-prompt.st")
    private Resource ragPromptResource;

    public AnswerService(RetrievalService retrievalService,
                         ChatModel chatModel,
                         QueryLogService queryLogService) {
        this.retrievalService = retrievalService;
        this.chatModel = chatModel;
        this.queryLogService = queryLogService;
    }

    /**
     * Answers a user question strictly from uploaded policy documents.
     */
    public AskResponse ask(String question) {
        long startTime = System.currentTimeMillis();

        if (question == null || question.isBlank()) {
            return new AskResponse(false, FALLBACK_MESSAGE, 0.0, Collections.emptyList());
        }

        String trimmedQuestion = question.trim();

        // 1. Retrieve top-K chunks from vector store
        List<Document> matchedChunks = retrievalService.retrieveTopK(trimmedQuestion);
        double threshold = retrievalService.getSimilarityThreshold();

        // Determine top similarity score
        double bestScore = 0.0;
        if (!matchedChunks.isEmpty() && matchedChunks.get(0).getScore() != null) {
            bestScore = matchedChunks.get(0).getScore();
        }

        double roundedConfidence = Math.round(bestScore * 100.0) / 100.0;

        // 2. Refusal check: If evidence is below similarity threshold or empty, do NOT call LLM
        if (matchedChunks.isEmpty() || bestScore < threshold) {
            logger.info("Refusing query '{}': best score {} is below threshold {}",
                    trimmedQuestion, bestScore, threshold);

            long latencyMs = System.currentTimeMillis() - startTime;
            queryLogService.logQuery(trimmedQuestion, FALLBACK_MESSAGE, bestScore, false,
                    Collections.emptyList(), latencyMs);

            return new AskResponse(false, FALLBACK_MESSAGE, roundedConfidence, Collections.emptyList());
        }

        // 3. Evidence is sufficient -> Prepare grounded context from top chunks
        StringBuilder contextBuilder = new StringBuilder();
        List<Citation> citations = new ArrayList<>();

        for (int i = 0; i < matchedChunks.size(); i++) {
            Document chunk = matchedChunks.get(i);
            String docName = (String) chunk.getMetadata().getOrDefault("document", "Unknown Document");

            Integer pageNumber = 1;
            Object pageObj = chunk.getMetadata().get("page");
            if (pageObj instanceof Number num) {
                pageNumber = num.intValue();
            } else if (pageObj != null) {
                try {
                    pageNumber = Integer.parseInt(pageObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }

            String chunkText = chunk.getText() != null ? chunk.getText() : "";

            contextBuilder.append(String.format("[Source %d | Document: %s, Page: %d]\n%s\n\n",
                    i + 1, docName, pageNumber, chunkText));

            // Generate clean snippet for citation
            String snippet = chunkText.length() > 180 ? chunkText.substring(0, 180).trim() + "..." : chunkText;
            citations.add(new Citation(docName, pageNumber, snippet));
        }

        // 4. Call Gemini via ChatModel with the grounded prompt template
        String answer;
        try {
            PromptTemplate template = new PromptTemplate(ragPromptResource);
            Prompt prompt = template.create(Map.of(
                    "context", contextBuilder.toString(),
                    "question", trimmedQuestion
            ));

            ChatResponse response = chatModel.call(prompt);
            answer = response.getResult().getOutput().getText();

            if (answer == null || answer.isBlank()) {
                answer = FALLBACK_MESSAGE;
            }
        } catch (Exception e) {
            logger.error("LLM call failed for query '{}': {}", trimmedQuestion, e.getMessage(), e);
            answer = "An error occurred while generating the answer. Please try again later.";
        }

        long latencyMs = System.currentTimeMillis() - startTime;
        boolean answered = !answer.contains(FALLBACK_MESSAGE);

        // 5. Persist query audit log to MySQL
        queryLogService.logQuery(trimmedQuestion, answer, bestScore, answered, citations, latencyMs);

        return new AskResponse(answered, answer.trim(), roundedConfidence, citations);
    }
}
