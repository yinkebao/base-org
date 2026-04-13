package com.baseorg.docassistant.dto.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalCandidate {
    private SearchResult.ResultItem item;
    private double vectorScore;
    private double keywordScore;
    private boolean titleMatched;
    private boolean sectionMatched;
}
