package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具执行后得到的可供回答使用的证据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolEvidence {
    private String evidenceId;
    private String toolId;
    private String toolName;
    private String sourceType;
    private String title;
    private String content;
    private Map<String, Object> metadata;
}
