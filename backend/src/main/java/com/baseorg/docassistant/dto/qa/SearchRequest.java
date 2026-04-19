package com.baseorg.docassistant.dto.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 向量搜索请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    private String query;
    private int topK;
    private double scoreThreshold;
    private Long userId;
    private List<String> sensitivityLevels;
}
