package com.praveen.ragassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.ragassistant.dto.AskRequest;
import com.praveen.ragassistant.dto.AskResponse;
import com.praveen.ragassistant.dto.Citation;
import com.praveen.ragassistant.service.AnswerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AskController.class)
class AskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnswerService answerService;

    @Test
    @DisplayName("POST /api/ask should return 200 OK with answer, confidence, and citations")
    void shouldReturnAnswerForValidQuestion() throws Exception {
        String question = "How many days of annual leave do employees accrue?";
        AskRequest request = new AskRequest(question);

        List<Citation> citations = List.of(
                new Citation("hr-leave-policy.pdf", 1, "Employees accrue 18 days of paid Annual Leave...")
        );
        AskResponse mockResponse = new AskResponse(
                true,
                "Employees accrue 18 days of paid annual leave per year.",
                0.88,
                citations
        );

        when(answerService.ask(eq(question))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true))
                .andExpect(jsonPath("$.answer").value("Employees accrue 18 days of paid annual leave per year."))
                .andExpect(jsonPath("$.confidence").value(0.88))
                .andExpect(jsonPath("$.citations[0].document").value("hr-leave-policy.pdf"))
                .andExpect(jsonPath("$.citations[0].page").value(1))
                .andExpect(jsonPath("$.citations[0].snippet").value("Employees accrue 18 days of paid Annual Leave..."));
    }

    @Test
    @DisplayName("POST /api/ask should return 400 Bad Request when question is blank")
    void shouldReturnBadRequestWhenQuestionIsBlank() throws Exception {
        AskRequest emptyRequest = new AskRequest("   ");

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
