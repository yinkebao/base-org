package com.baseorg.docassistant.dto.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 向量搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private List<ResultItem> items;
    private long total;
    private long processingTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultItem {
        private Long chunkId;
        private Long docId;
        private String docTitle;
        private String content;
        private double score;
        private int chunkIndex;
        private String sectionTitle;
        private Map<String, Object> metadata;
    }
}
