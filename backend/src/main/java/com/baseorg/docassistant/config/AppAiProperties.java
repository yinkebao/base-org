package com.baseorg.docassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 应用内部 AI 运行时配置。
 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AppAiProperties {

    private Provider provider = Provider.OPENAI_COMPATIBLE;

    @NestedConfigurationProperty
    private CapabilityProperties chat = new CapabilityProperties();

    @NestedConfigurationProperty
    private CapabilityProperties embedding = new CapabilityProperties();

    private boolean degradedFallbackEnabled = true;

    private boolean logStartupStatus = true;

    public Provider resolveChatProvider() {
        return chat.getProvider() != null ? chat.getProvider() : provider;
    }

    public Provider resolveEmbeddingProvider() {
        return embedding.getProvider() != null ? embedding.getProvider() : provider;
    }

    @Data
    public static class CapabilityProperties {
        private Provider provider;
    }

    public enum Provider {
        OPENAI_COMPATIBLE,
        OLLAMA,
        DASHSCOPE
    }
}
