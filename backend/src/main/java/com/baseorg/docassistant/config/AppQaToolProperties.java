package com.baseorg.docassistant.config;

import com.baseorg.docassistant.dto.qa.tool.ToolProviderType;
import com.baseorg.docassistant.dto.qa.tool.ToolType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QA 工具编排配置。
 */
@Data
@ConfigurationProperties(prefix = "app.qa.tools")
public class AppQaToolProperties {

    private boolean plannerLlmEnabled = true;

    private int maxSteps = 3;

    private List<CatalogItem> catalog = new ArrayList<>();

    @Data
    public static class CatalogItem {
        private String toolId;
        private ToolType type;
        private ToolProviderType providerType = ToolProviderType.LOCAL;
        private String displayName;
        private String description;
        private String serverName;
        private String remoteToolName;
        private boolean enabled = true;
        private boolean readOnly = true;
        private boolean supportsStreaming = false;
        private int priority = 100;
        private List<String> keywords = new ArrayList<>();
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }
}
