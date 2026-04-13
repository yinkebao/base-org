package com.baseorg.docassistant.dto.qa;

import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.ToolTraceItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QAMessageResponse {
    private Long messageId;
    private String role;
    private String content;
    private String rewrittenQuery;
    private List<QAResponse.SourceChunk> sources;
    private Double confidence;
    private String promptHash;
    private String intent;
    private String status;
    private Boolean degraded;
    private String degradeReason;
    private String fallbackMode;
    private String errorCode;
    private Long processingTimeMs;
    private String planSummary;
    private List<ToolTraceItem> toolTrace;
    private List<DiagramPayload> diagrams;
    private List<ToolEvidence> externalSources;
    private LocalDateTime createdAt;
}
