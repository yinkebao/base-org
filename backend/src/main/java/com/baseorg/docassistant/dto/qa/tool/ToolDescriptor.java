package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具元数据描述。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDescriptor {
    private String toolId;
    private ToolType type;
    private ToolProviderType providerType;
    private String displayName;
    private String description;
    private String serverName;
    private String remoteToolName;
    private boolean enabled;
    private boolean readOnly;
    private boolean supportsStreaming;
    private int priority;
    private List<String> keywords;
    private Map<String, Object> metadata;
}
