package com.baseorg.docassistant.service.rag;

import com.baseorg.docassistant.config.AppAiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 基于 Spring AI Bean 的运行时实现。
 */
@Slf4j
@RequiredArgsConstructor
public class SpringAiRuntime implements AiRuntime {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AppAiProperties appAiProperties;

    @PostConstruct
    void logAvailability() {
        if (!appAiProperties.isLogStartupStatus()) {
            return;
        }

        boolean chatAvailable = chatAvailable();
        boolean embeddingAvailable = embeddingAvailable();
        AppAiProperties.Provider chatProvider = appAiProperties.resolveChatProvider();
        AppAiProperties.Provider embeddingProvider = appAiProperties.resolveEmbeddingProvider();

        log.info("AI 运行时初始化完成: chatProvider={}, embeddingProvider={}, chatAvailable={}, embeddingAvailable={}, degradedFallbackEnabled={}",
                chatProvider, embeddingProvider, chatAvailable, embeddingAvailable, appAiProperties.isDegradedFallbackEnabled());

        if (!chatAvailable) {
            log.warn("AI ChatClient.Builder 不可用: provider={}, expectedPropertyPrefix={}",
                    chatProvider, expectedChatPropertyPrefix(chatProvider));
        }

        if (!embeddingAvailable) {
            log.warn("AI EmbeddingModel 不可用: provider={}, expectedPropertyPrefix={}",
                    embeddingProvider, expectedEmbeddingPropertyPrefix(embeddingProvider));
        }
    }

    @Override
    public boolean chatAvailable() {
        return chatClientBuilderProvider.getIfAvailable() != null;
    }

    @Override
    public boolean embeddingAvailable() {
        return embeddingModelProvider.getIfAvailable() != null;
    }

    @Override
    public ChatClient createChatClient() {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new IllegalStateException("ChatClient.Builder 未配置");
        }
        return builder.build();
    }

    @Override
    public EmbeddingModel getEmbeddingModel() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("EmbeddingModel 未配置");
        }
        return model;
    }

    private String expectedChatPropertyPrefix(AppAiProperties.Provider provider) {
        return switch (provider) {
            case OPENAI_COMPATIBLE -> "spring.ai.openai.chat";
            case OLLAMA -> "spring.ai.ollama.chat";
            default -> "n/a";
        };
    }

    private String expectedEmbeddingPropertyPrefix(AppAiProperties.Provider provider) {
        return switch (provider) {
            case OPENAI_COMPATIBLE -> "spring.ai.openai.embedding";
            case OLLAMA -> "spring.ai.ollama.embedding";
            default -> "n/a";
        };
    }
}
