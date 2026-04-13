package com.baseorg.docassistant.config;

import com.baseorg.docassistant.service.rag.AiRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiProviderConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiProviderConfig.class)
            .withPropertyValues("app.ai.log-startup-status=false");

    @Test
    void shouldStartWhenNoAiBeansPresent() {
        contextRunner.run(context -> {
            AiRuntime aiRuntime = context.getBean(AiRuntime.class);

            assertThat(aiRuntime.chatAvailable()).isFalse();
            assertThat(aiRuntime.embeddingAvailable()).isFalse();
        });
    }

    @Test
    void shouldStartWhenOnlyChatBeanPresent() {
        contextRunner.withUserConfiguration(ChatOnlyConfig.class).run(context -> {
            AiRuntime aiRuntime = context.getBean(AiRuntime.class);

            assertThat(aiRuntime.chatAvailable()).isTrue();
            assertThat(aiRuntime.embeddingAvailable()).isFalse();
            assertThat(aiRuntime.createChatClient()).isNotNull();
        });
    }

    @Test
    void shouldStartWhenOnlyEmbeddingBeanPresent() {
        contextRunner.withUserConfiguration(EmbeddingOnlyConfig.class).run(context -> {
            AiRuntime aiRuntime = context.getBean(AiRuntime.class);

            assertThat(aiRuntime.chatAvailable()).isFalse();
            assertThat(aiRuntime.embeddingAvailable()).isTrue();
            assertThat(aiRuntime.getEmbeddingModel()).isNotNull();
        });
    }

    @Test
    void shouldStartWhenAllAiBeansPresent() {
        contextRunner.withUserConfiguration(AllAiBeansConfig.class).run(context -> {
            AiRuntime aiRuntime = context.getBean(AiRuntime.class);

            assertThat(aiRuntime.chatAvailable()).isTrue();
            assertThat(aiRuntime.embeddingAvailable()).isTrue();
        });
    }

    @Test
    void shouldBindApplicationAndOpenAiProperties() {
        contextRunner
                .withPropertyValues(
                        "app.ai.provider=OPENAI_COMPATIBLE",
                        "app.ai.chat.provider=OPENAI_COMPATIBLE",
                        "app.ai.embedding.provider=OLLAMA",
                        "app.ai.degraded-fallback-enabled=false",
                        "spring.ai.openai.api-key=test-key",
                        "spring.ai.openai.base-url=https://example.invalid",
                        "spring.ai.openai.chat.options.model=gpt-4o-mini",
                        "spring.ai.ollama.base-url=http://localhost:11434",
                        "spring.ai.ollama.embedding.options.model=bge-m3")
                .run(context -> {
                    AppAiProperties properties = context.getBean(AppAiProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(AppAiProperties.Provider.OPENAI_COMPATIBLE);
                    assertThat(properties.resolveChatProvider()).isEqualTo(AppAiProperties.Provider.OPENAI_COMPATIBLE);
                    assertThat(properties.resolveEmbeddingProvider()).isEqualTo(AppAiProperties.Provider.OLLAMA);
                    assertThat(properties.isDegradedFallbackEnabled()).isFalse();
                    assertThat(context.getEnvironment().getProperty("spring.ai.openai.api-key")).isEqualTo("test-key");
                    assertThat(context.getEnvironment().getProperty("spring.ai.openai.base-url"))
                            .isEqualTo("https://example.invalid");
                    assertThat(context.getEnvironment().getProperty("spring.ai.openai.chat.options.model"))
                            .isEqualTo("gpt-4o-mini");
                    assertThat(context.getEnvironment().getProperty("spring.ai.ollama.base-url"))
                            .isEqualTo("http://localhost:11434");
                    assertThat(context.getEnvironment().getProperty("spring.ai.ollama.embedding.options.model"))
                            .isEqualTo("bge-m3");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ChatOnlyConfig {

        @Bean
        ChatClient.Builder chatClientBuilder() {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EmbeddingOnlyConfig {

        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AllAiBeansConfig {

        @Bean
        ChatClient.Builder chatClientBuilder() {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }

        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }
    }
}
