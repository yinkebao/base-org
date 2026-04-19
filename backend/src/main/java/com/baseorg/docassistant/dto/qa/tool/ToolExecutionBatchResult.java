package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个阶段的工具批量执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionBatchResult {
    private List<ToolTraceItem> traceItems;
    private List<ToolEvidence> evidences;
    private List<DiagramPayload> diagrams;
}
