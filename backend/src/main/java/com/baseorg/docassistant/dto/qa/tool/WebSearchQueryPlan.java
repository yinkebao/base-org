package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 联网搜索查询规划结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchQueryPlan {
    private String originalQuestion;
    private String normalizedQuery;
    private String primaryQuery;
    private List<String> queries;
    private WebSearchSummaryMode summaryMode;
}
