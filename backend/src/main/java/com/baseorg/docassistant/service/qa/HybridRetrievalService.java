package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.RetrievalCandidate;
import com.baseorg.docassistant.dto.qa.SearchRequest;
import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.service.rag.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 混合检索服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final VectorSearchService vectorSearchService;
    private final GraphRecallService graphRecallService;
    private final JdbcTemplate jdbcTemplate;

    public List<RetrievalCandidate> retrieve(QueryPlan queryPlan, SearchRequest searchRequest) {
        Map<Long, RetrievalCandidate> merged = new LinkedHashMap<>();

        SearchResult vectorResult = vectorSearchService.search(searchRequest);
        vectorResult.getItems().forEach(item -> merged.put(item.getChunkId(),
                RetrievalCandidate.builder()
                        .item(item)
                        .vectorScore(item.getScore())
                        .keywordScore(0.0)
                        .titleMatched(false)
                        .sectionMatched(matchesSection(queryPlan.getRewrittenQuery(), item.getSectionTitle()))
                        .build()));

        keywordSearch(queryPlan.getRewrittenQuery(), searchRequest).forEach(item -> {
            RetrievalCandidate candidate = merged.get(item.getChunkId());
            if (candidate == null) {
                merged.put(item.getChunkId(), RetrievalCandidate.builder()
                        .item(item)
                        .vectorScore(0.0)
                        .keywordScore(item.getScore())
                        .titleMatched(matchesTitle(queryPlan.getRewrittenQuery(), item.getDocTitle()))
                        .sectionMatched(matchesSection(queryPlan.getRewrittenQuery(), item.getSectionTitle()))
                        .build());
                return;
            }
            candidate.setKeywordScore(Math.max(candidate.getKeywordScore(), item.getScore()));
            candidate.setTitleMatched(candidate.isTitleMatched() || matchesTitle(queryPlan.getRewrittenQuery(), item.getDocTitle()));
            candidate.setSectionMatched(candidate.isSectionMatched() || matchesSection(queryPlan.getRewrittenQuery(), item.getSectionTitle()));
        });

        graphRecallService.recall(queryPlan, searchRequest).forEach(item ->
                merged.computeIfAbsent(item.getChunkId(), ignored -> RetrievalCandidate.builder()
                        .item(item)
                        .vectorScore(0.0)
                        .keywordScore(0.0)
                        .titleMatched(false)
                        .sectionMatched(false)
                        .build()));

        return new ArrayList<>(merged.values());
    }

    private List<SearchResult.ResultItem> keywordSearch(String query, SearchRequest searchRequest) {
        try {
            String sql = """
                    SELECT c.id, c.doc_id, c.chunk_index, c.text, c.section_title, c.metadata,
                           d.title AS doc_title,
                           ts_rank_cd(to_tsvector('simple', coalesce(c.text, '')), plainto_tsquery('simple', ?)) AS rank_score
                    FROM chunks c
                    JOIN documents d ON d.id = c.doc_id
                    WHERE d.owner_id = ?
                      AND d.status = 'PUBLISHED'
                      AND d.sensitivity IN ('PUBLIC', 'INTERNAL')
                      AND to_tsvector('simple', coalesce(c.text, '')) @@ plainto_tsquery('simple', ?)
                    ORDER BY rank_score DESC
                    LIMIT ?
                    """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    sql,
                    query,
                    searchRequest.getUserId(),
                    query,
                    searchRequest.getTopK()
            );
            double maxScore = rows.stream()
                    .map(row -> ((Number) row.get("rank_score")).doubleValue())
                    .max(Double::compareTo)
                    .orElse(1.0);

            return rows.stream()
                    .map(row -> SearchResult.ResultItem.builder()
                            .chunkId(((Number) row.get("id")).longValue())
                            .docId(((Number) row.get("doc_id")).longValue())
                            .chunkIndex(((Number) row.get("chunk_index")).intValue())
                            .content((String) row.get("text"))
                            .sectionTitle((String) row.get("section_title"))
                            .docTitle((String) row.get("doc_title"))
                            .metadata(parseMetadata(row.get("metadata")))
                            .score(maxScore <= 0 ? 0.0 : ((Number) row.get("rank_score")).doubleValue() / maxScore)
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("关键词召回失败: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean matchesTitle(String query, String title) {
        if (query == null || title == null) {
            return false;
        }
        return title.contains(query) || query.contains(title);
    }

    private boolean matchesSection(String query, String section) {
        if (query == null || section == null) {
            return false;
        }
        return query.contains(section) || section.contains(query);
    }

    private Map<String, Object> parseMetadata(Object value) {
        if (value == null) {
            return Map.of();
        }
        String raw = value instanceof PGobject pgObject ? pgObject.getValue() : value.toString();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return Map.of("raw", raw);
    }
}
