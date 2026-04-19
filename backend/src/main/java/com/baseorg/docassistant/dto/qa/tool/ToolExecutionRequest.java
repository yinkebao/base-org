package com.baseorg.docassistant.dto.qa.tool;

import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.SearchResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工具执行请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionRequest {
    private Long userId;
    private Long sessionId;
    private String question;
    private QueryPlan queryPlan;
    private ToolDescriptor descriptor;
    private ToolStep step;
    private List<ToolEvidence> accumulatedEvidence;
    private List<SearchResult.ResultItem> retrievalItems;
}
