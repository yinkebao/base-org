package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工具规划结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPlan {
    private String planId;
    private ToolExecutionStrategy strategy;
    private String summary;
    private boolean llmSelected;
    private List<String> candidateToolIds;
    private List<ToolStep> steps;
}
