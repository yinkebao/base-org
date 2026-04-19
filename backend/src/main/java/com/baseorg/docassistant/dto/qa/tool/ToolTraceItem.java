package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具执行轨迹。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolTraceItem {
    private String stepId;
    private String toolId;
    private String toolName;
    private String goal;
    private String status;
    private String summary;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
