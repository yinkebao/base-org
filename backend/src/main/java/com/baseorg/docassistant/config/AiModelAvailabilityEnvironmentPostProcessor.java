package com.baseorg.docassistant.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在缺少 AI 凭证时关闭对应模型自动配置，保证应用仍可降级启动。
 */
public class AiModelAvailabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "appAiModelAvailabilityOverrides";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        disableUnusedOpenAiModelFamilies(overrides);

        configureChatModel(environment, overrides);
        configureEmbeddingModel(environment, overrides);

        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private void disableUnusedOpenAiModelFamilies(Map<String, Object> overrides) {
        overrides.put("spring.ai.model.audio.speech", "none");
        overrides.put("spring.ai.model.audio.transcription", "none");
        overrides.put("spring.ai.model.image", "none");
        overrides.put("spring.ai.model.moderation", "none");
    }

    private void configureChatModel(ConfigurableEnvironment environment, Map<String, Object> overrides) {
        AppAiProperties.Provider provider = resolveChatProvider(environment);
        switch (provider) {
            case OPENAI_COMPATIBLE -> {
                if (hasOpenAiChatConfiguration(environment)) {
                    overrides.put("spring.ai.model.chat", "openai");
                } else {
                    overrides.put("spring.ai.model.chat", "none");
                    overrides.put("spring.ai.chat.client.enabled", "false");
                }
            }
            case OLLAMA -> {
                if (hasOllamaChatConfiguration(environment)) {
                    overrides.put("spring.ai.model.chat", "ollama");
                } else {
                    overrides.put("spring.ai.model.chat", "none");
                    overrides.put("spring.ai.chat.client.enabled", "false");
                }
            }
            default -> {
                overrides.put("spring.ai.model.chat", "none");
                overrides.put("spring.ai.chat.client.enabled", "false");
            }
        }
    }

    private void configureEmbeddingModel(ConfigurableEnvironment environment, Map<String, Object> overrides) {
        AppAiProperties.Provider provider = resolveEmbeddingProvider(environment);
        switch (provider) {
            case OPENAI_COMPATIBLE -> {
                if (hasOpenAiEmbeddingConfiguration(environment)) {
                    overrides.put("spring.ai.model.embedding", "openai");
                } else {
                    overrides.put("spring.ai.model.embedding", "none");
                }
            }
            case OLLAMA -> {
                if (hasOllamaEmbeddingConfiguration(environment)) {
                    overrides.put("spring.ai.model.embedding", "ollama");
                } else {
                    overrides.put("spring.ai.model.embedding", "none");
                }
            }
            default -> overrides.put("spring.ai.model.embedding", "none");
        }
    }

    private AppAiProperties.Provider resolveChatProvider(ConfigurableEnvironment environment) {
        return resolveProvider(firstNonBlank(
                environment.getProperty("app.ai.chat.provider"),
                environment.getProperty("APP_AI_CHAT_PROVIDER")),
                resolveLegacyProvider(environment));
    }

    private AppAiProperties.Provider resolveEmbeddingProvider(ConfigurableEnvironment environment) {
        return resolveProvider(firstNonBlank(
                environment.getProperty("app.ai.embedding.provider"),
                environment.getProperty("APP_AI_EMBEDDING_PROVIDER")),
                resolveLegacyProvider(environment));
    }

    private AppAiProperties.Provider resolveLegacyProvider(ConfigurableEnvironment environment) {
        return resolveProvider(firstNonBlank(
                environment.getProperty("app.ai.provider"),
                environment.getProperty("APP_AI_PROVIDER")),
                AppAiProperties.Provider.OPENAI_COMPATIBLE);
    }

    private AppAiProperties.Provider resolveProvider(String value, AppAiProperties.Provider fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return AppAiProperties.Provider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private boolean hasOpenAiChatConfiguration(ConfigurableEnvironment environment) {
        return StringUtils.hasText(firstNonBlank(
                environment.getProperty("spring.ai.openai.chat.api-key"),
                environment.getProperty("SPRING_AI_OPENAI_CHAT_API_KEY"),
                environment.getProperty("spring.ai.openai.api-key"),
                environment.getProperty("SPRING_AI_OPENAI_API_KEY"),
                environment.getProperty("OPENAI_API_KEY")));
    }

    private boolean hasOpenAiEmbeddingConfiguration(ConfigurableEnvironment environment) {
        return StringUtils.hasText(firstNonBlank(
                environment.getProperty("spring.ai.openai.embedding.api-key"),
                environment.getProperty("SPRING_AI_OPENAI_EMBEDDING_API_KEY"),
                environment.getProperty("spring.ai.openai.api-key"),
                environment.getProperty("SPRING_AI_OPENAI_API_KEY"),
                environment.getProperty("OPENAI_API_KEY")));
    }

    private boolean hasOllamaChatConfiguration(ConfigurableEnvironment environment) {
        return StringUtils.hasText(firstNonBlank(
                environment.getProperty("spring.ai.ollama.base-url"),
                environment.getProperty("SPRING_AI_OLLAMA_BASE_URL")))
                && StringUtils.hasText(firstNonBlank(
                environment.getProperty("spring.ai.ollama.chat.options.model"),
                environment.getProperty("SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL")));
    }

    private boolean hasOllamaEmbeddingConfiguration(ConfigurableEnvironment environment) {
        return StringUtils.hasText(firstNonBlank(
                environment.getProperty("spring.ai.ollama.base-url"),
                environment.getProperty("SPRING_AI_OLLAMA_BASE_URL")))
                && StringUtils.hasText(firstNonBlank(
                environment.getProperty("spring.ai.ollama.embedding.options.model"),
                environment.getProperty("SPRING_AI_OLLAMA_EMBEDDING_OPTIONS_MODEL")));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
