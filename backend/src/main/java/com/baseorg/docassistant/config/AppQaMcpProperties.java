package com.baseorg.docassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QA 远程 MCP 服务配置。
 */
@Data
@ConfigurationProperties(prefix = "app.qa.mcp")
public class AppQaMcpProperties {

    private Map<String, ServerConfig> servers = new LinkedHashMap<>();

    @Data
    public static class ServerConfig {
        private boolean enabled = true;
        private String baseUrl;
        private String authMode = "bearer";
        private String apiKey;
        private String apiKeyQueryParam;
        private int requestTimeoutSeconds = 15;
        private Map<String, String> headers = new LinkedHashMap<>();
        private List<String> expectedToolNames = new ArrayList<>();
    }
}
