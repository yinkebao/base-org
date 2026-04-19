package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.SearchRequest;
import com.baseorg.docassistant.dto.qa.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识图谱召回占位实现
 */
@Service
public class GraphRecallService {

    public List<SearchResult.ResultItem> recall(QueryPlan queryPlan, SearchRequest searchRequest) {
        return List.of();
    }
}
