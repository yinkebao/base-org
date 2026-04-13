package com.baseorg.docassistant.dto.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryPlan {
    private Intent intent;
    private String rawQuestion;
    private String rewrittenQuery;
    private String historySummary;
    private boolean shouldSkipRetrieval;
    private boolean toolPlanned;
    private boolean graphPlanned;
    private boolean diagramRequested;
    private String diagramTypeHint;
    private String toolHint;

    public enum Intent {
        RAG_SEARCH,
        SMALL_TALK
    }
}
