package com.baseorg.docassistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AiModelAvailabilityEnvironmentPostProcessorTest {

    private final AiModelAvailabilityEnvironmentPostProcessor postProcessor =
            new AiModelAvailabilityEnvironmentPostProcessor();

    @Test
    void shouldDisableChatAndEmbeddingWhenNoApiKeyProvided() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.chat.client.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.image")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.moderation")).isEqualTo("none");
    }

    @Test
    void shouldKeepBothModelsEnabledWhenSharedApiKeyProvided() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("OPENAI_API_KEY", "test-key");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("openai");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("openai");
        assertThat(environment.getProperty("spring.ai.chat.client.enabled")).isNull();
        assertThat(environment.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.image")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.moderation")).isEqualTo("none");
    }

    @Test
    void shouldDisableOnlyEmbeddingWhenOnlyChatApiKeyProvided() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("SPRING_AI_OPENAI_CHAT_API_KEY", "chat-key");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("openai");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.chat.client.enabled")).isNull();
    }

    @Test
    void shouldDisableAllOpenAiModelsWhenProviderSwitchesAway() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_AI_PROVIDER", "DASHSCOPE")
                .withProperty("OPENAI_API_KEY", "test-key");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.chat.client.enabled")).isEqualTo("false");
    }

    @Test
    void shouldEnableOpenAiChatAndOllamaEmbeddingIndependently() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_AI_CHAT_PROVIDER", "OPENAI_COMPATIBLE")
                .withProperty("APP_AI_EMBEDDING_PROVIDER", "OLLAMA")
                .withProperty("OPENAI_API_KEY", "test-key")
                .withProperty("SPRING_AI_OLLAMA_BASE_URL", "http://localhost:11434")
                .withProperty("SPRING_AI_OLLAMA_EMBEDDING_OPTIONS_MODEL", "bge-m3");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("openai");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("ollama");
        assertThat(environment.getProperty("spring.ai.chat.client.enabled")).isNull();
    }

    @Test
    void shouldDisableOnlyChatWhenOpenAiKeyMissingButOllamaEmbeddingConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_AI_CHAT_PROVIDER", "OPENAI_COMPATIBLE")
                .withProperty("APP_AI_EMBEDDING_PROVIDER", "OLLAMA")
                .withProperty("SPRING_AI_OLLAMA_BASE_URL", "http://localhost:11434")
                .withProperty("SPRING_AI_OLLAMA_EMBEDDING_OPTIONS_MODEL", "bge-m3");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("ollama");
        assertThat(environment.getProperty("spring.ai.chat.client.enabled")).isEqualTo("false");
    }
}
