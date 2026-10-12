package com.praveen.ragassistant.service;

import com.praveen.ragassistant.dto.AskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private QueryLogService queryLogService;

    private AnswerService answerService;

    @BeforeEach
    void setUp() {
        answerService = new AnswerService(retrievalService, chatModel, queryLogService);
        String promptContent = "Context: {context}\nQuestion: {question}";
        ReflectionTestUtils.setField(answerService, "ragPromptResource",
                new ByteArrayResource(promptContent.getBytes()));
    }

    @Test
    @DisplayName("Should REFUSE and NOT call LLM when best similarity score is below threshold")
    void shouldRefuseAndNotCallLlmWhenBelowThreshold() {
        String question = "Are employees allowed to bring pet dogs to the office?";

        // Mock document with weak similarity score (0.42 < 0.65 threshold)
        Document weakMatch = Document.builder()
                .text("Casual leave is 12 days per year.")
                .metadata(Map.of("document", "hr-leave-policy.pdf", "page", 1))
                .score(0.42)
                .build();

        when(retrievalService.retrieveTopK(question)).thenReturn(List.of(weakMatch));
        when(retrievalService.getSimilarityThreshold()).thenReturn(0.65);

        // Execute ask
        AskResponse response = answerService.ask(question);

        // Verify refusal response
        assertNotNull(response);
        assertFalse(response.answered(), "Response should mark answered=false when below threshold");
        assertEquals(AnswerService.FALLBACK_MESSAGE, response.answer());
        assertEquals(0.42, response.confidence());
        assertTrue(response.citations().isEmpty());

        // CRITICAL CHECK: LLM was NEVER called
        verify(chatModel, never()).call(any(Prompt.class));

        // Verify query was logged as unanswered
        verify(queryLogService).logQuery(
                eq(question),
                eq(AnswerService.FALLBACK_MESSAGE),
                eq(0.42),
                eq(false),
                eq(Collections.emptyList()),
                anyLong()
        );
    }

    @Test
    @DisplayName("Should REFUSE and NOT call LLM when retrieval returns empty results")
    void shouldRefuseWhenNoDocumentsRetrieved() {
        String question = "What is the corporate policy on alien abduction?";

        when(retrievalService.retrieveTopK(question)).thenReturn(Collections.emptyList());
        when(retrievalService.getSimilarityThreshold()).thenReturn(0.65);

        AskResponse response = answerService.ask(question);

        assertFalse(response.answered());
        assertEquals(AnswerService.FALLBACK_MESSAGE, response.answer());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should call LLM and return grounded response with citations when similarity score >= threshold")
    void shouldCallLlmAndReturnGroundedAnswerWhenAboveThreshold() {
        String question = "How many days of annual leave do employees accrue?";

        // Mock document with strong similarity score (0.89 >= 0.65)
        Document strongMatch = Document.builder()
                .text("Employees accrue 18 days of paid Annual Leave per calendar year.")
                .metadata(Map.of("document", "hr-leave-policy.pdf", "page", 1))
                .score(0.89)
                .build();

        when(retrievalService.retrieveTopK(question)).thenReturn(List.of(strongMatch));
        when(retrievalService.getSimilarityThreshold()).thenReturn(0.65);

        // Mock ChatModel response
        String llmGeneratedAnswer = "Employees accrue 18 days of paid annual leave per calendar year [Document: hr-leave-policy.pdf, Page: 1].";
        ChatResponse mockChatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(llmGeneratedAnswer)))
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        // Execute ask
        AskResponse response = answerService.ask(question);

        // Verify response
        assertNotNull(response);
        assertTrue(response.answered(), "Response should mark answered=true");
        assertEquals(llmGeneratedAnswer, response.answer());
        assertEquals(0.89, response.confidence());
        assertEquals(1, response.citations().size());

        assertEquals("hr-leave-policy.pdf", response.citations().get(0).document());
        assertEquals(1, response.citations().get(0).page());

        // Verify LLM was indeed called
        verify(chatModel).call(any(Prompt.class));

        // Verify query was logged as answered
        verify(queryLogService).logQuery(
                eq(question),
                eq(llmGeneratedAnswer),
                eq(0.89),
                eq(true),
                any(),
                anyLong()
        );
    }
}
