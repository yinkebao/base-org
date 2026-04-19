package com.baseorg.docassistant.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LLMServiceTest {

    @Mock
    private AiRuntime aiRuntime;

    private LLMService llmService;

    @BeforeEach
    void setUp() {
        llmService = new LLMService(aiRuntime);
    }

    @Test
    void shouldReturnNullWhenChatNotAvailable() {
        // Given
        when(aiRuntime.chatAvailable()).thenReturn(false);

        // When
        String result = llmService.generate("system", "user");

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenRuntimeThrowsException() {
        // Given
        when(aiRuntime.chatAvailable()).thenReturn(true);
        when(aiRuntime.createChatClient()).thenThrow(new RuntimeException("AI runtime error"));

        // When
        String result = llmService.generate("system", "user");

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldCheckAvailabilityCorrectly() {
        // Given
        when(aiRuntime.chatAvailable()).thenReturn(true);

        // When
        boolean available = llmService.isAvailable();

        // Then
        assertThat(available).isTrue();
    }

    @Test
    void shouldReturnNotAvailableWhenAiRuntimeDown() {
        // Given
        when(aiRuntime.chatAvailable()).thenReturn(false);

        // When
        boolean available = llmService.isAvailable();

        // Then
        assertThat(available).isFalse();
    }

    @Test
    void shouldReturnNullForRagAnswerWhenNotAvailable() {
        // Given
        when(aiRuntime.chatAvailable()).thenReturn(false);

        // When
        String result = llmService.generateRagAnswer("问题", "上下文");

        // Then
        assertThat(result).isNull();
    }
}
