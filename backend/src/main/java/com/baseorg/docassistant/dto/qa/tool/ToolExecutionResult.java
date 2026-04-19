package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {
    private String stepId;
    private String toolId;
    private boolean success;
    private String summary;
    private String errorMessage;
    private List<ToolEvidence> evidences;
    private List<DiagramPayload> diagrams;
    private Map<String, Object> output;
}
