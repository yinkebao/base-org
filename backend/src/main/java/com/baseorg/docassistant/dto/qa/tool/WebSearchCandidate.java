package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部搜索候选结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchCandidate {
    private String title;
    private String snippet;
    private String url;
    private String domain;
    private String publishedAt;
    private double score;
    private double qualityScore;
    private int candidateRank;
    private String sourceCategory;
}
