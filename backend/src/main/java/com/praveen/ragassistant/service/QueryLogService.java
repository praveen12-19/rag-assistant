package com.praveen.ragassistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.ragassistant.dto.Citation;
import com.praveen.ragassistant.dto.QueryLogResponse;
import com.praveen.ragassistant.entity.QueryLog;
import com.praveen.ragassistant.repository.QueryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing audit query logging in MySQL.
 * Tracks questions, generated answers, top similarity score, latency, and citations.
 */
@Service
public class QueryLogService {

    private static final Logger logger = LoggerFactory.getLogger(QueryLogService.class);

    private final QueryLogRepository queryLogRepository;
    private final ObjectMapper objectMapper;

    public QueryLogService(QueryLogRepository queryLogRepository, ObjectMapper objectMapper) {
        this.queryLogRepository = queryLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists an audit log entry for every user question.
     */
    @Transactional
    public QueryLog logQuery(String question, String answer, Double topScore, boolean answered,
                             List<Citation> citations, long latencyMs) {
        String citationsJson = "[]";
        if (citations != null && !citations.isEmpty()) {
            try {
                citationsJson = objectMapper.writeValueAsString(citations);
            } catch (JsonProcessingException e) {
                logger.warn("Could not serialize citations to JSON: {}", e.getMessage());
                citationsJson = citations.toString();
            }
        }

        QueryLog log = new QueryLog(
                question,
                answer,
                topScore != null ? Math.round(topScore * 1000.0) / 1000.0 : null,
                answered,
                citationsJson,
                latencyMs,
                LocalDateTime.now()
        );

        return queryLogRepository.save(log);
    }

    /**
     * Retrieves all query logs for the admin view.
     */
    @Transactional(readOnly = true)
    public List<QueryLogResponse> getAllLogs() {
        return queryLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves unanswered / refused queries where evidence was insufficient.
     */
    @Transactional(readOnly = true)
    public List<QueryLogResponse> getUnansweredLogs() {
        return queryLogRepository.findByAnsweredFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private QueryLogResponse toResponse(QueryLog entity) {
        return new QueryLogResponse(
                entity.getId(),
                entity.getQuestion(),
                entity.getAnswer(),
                entity.getTopScore(),
                entity.getAnswered(),
                entity.getCitations(),
                entity.getLatencyMs(),
                entity.getCreatedAt()
        );
    }
}
