package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单个工具执行步骤。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolStep {
    private String stepId;
    private String toolId;
    private ToolType toolType;
    private ToolExecutionPhase executionPhase;
    private String goal;
    private Map<String, Object> arguments;
    private boolean stopIfSatisfied;
    private boolean fallbackOnly;
}
