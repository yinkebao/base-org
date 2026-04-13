package com.baseorg.docassistant.config;

import com.baseorg.docassistant.service.rag.AiRuntime;
import com.baseorg.docassistant.service.rag.SpringAiRuntime;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Provider 装配。
 */
@Configuration
@EnableConfigurationProperties(AppAiProperties.class)
public class AiProviderConfig {

    @Bean
    public AiRuntime aiRuntime(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            AppAiProperties appAiProperties) {
        return new SpringAiRuntime(chatClientBuilderProvider, embeddingModelProvider, appAiProperties);
    }
}
