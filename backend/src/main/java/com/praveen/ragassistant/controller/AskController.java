package com.praveen.ragassistant.controller;

import com.praveen.ragassistant.dto.AskRequest;
import com.praveen.ragassistant.dto.AskResponse;
import com.praveen.ragassistant.service.AnswerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the verified RAG querying endpoint.
 */
@RestController
@RequestMapping("/api/ask")
public class AskController {

    private final AnswerService answerService;

    public AskController(AnswerService answerService) {
        this.answerService = answerService;
    }

    /**
     * Accepts a user question, retrieves relevant context from uploaded policy documents,
     * checks similarity threshold, and calls Gemini if sufficiently grounded.
     * POST /api/ask
     */
    @PostMapping
    public ResponseEntity<AskResponse> askQuestion(@Valid @RequestBody AskRequest request) {
        AskResponse response = answerService.ask(request.question());
        return ResponseEntity.ok(response);
    }
}
