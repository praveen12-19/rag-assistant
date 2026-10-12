package com.praveen.ragassistant.controller;

import com.praveen.ragassistant.dto.QueryLogResponse;
import com.praveen.ragassistant.service.QueryLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller providing administrative analytics and audit views.
 */
@RestController
@RequestMapping("/api")
public class AdminController {

    private final QueryLogService queryLogService;

    public AdminController(QueryLogService queryLogService) {
        this.queryLogService = queryLogService;
    }

    /**
     * Lists all logged user queries, confidence scores, and generated answers.
     * GET /api/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<QueryLogResponse>> getQueryHistory() {
        List<QueryLogResponse> history = queryLogService.getAllLogs();
        return ResponseEntity.ok(history);
    }

    /**
     * Lists queries that were refused because supporting evidence was weak (score < threshold).
     * GET /api/unanswered
     */
    @GetMapping("/unanswered")
    public ResponseEntity<List<QueryLogResponse>> getUnansweredQueries() {
        List<QueryLogResponse> unanswered = queryLogService.getUnansweredLogs();
        return ResponseEntity.ok(unanswered);
    }
}
