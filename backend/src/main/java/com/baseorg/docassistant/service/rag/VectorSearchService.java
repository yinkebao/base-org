package com.baseorg.docassistant.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baseorg.docassistant.dto.qa.SearchRequest;
import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.entity.Chunk;
import com.baseorg.docassistant.entity.Document;
import com.baseorg.docassistant.mapper.ChunkMapper;
import com.baseorg.docassistant.mapper.DocumentMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量检索服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final ChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 向量相似度搜索
     */
    @Transactional(readOnly = true)
    public SearchResult search(SearchRequest request) {
        long startTime = System.currentTimeMillis();
        log.debug("开始向量检索: query={}, topK={}", request.getQuery(), request.getTopK());

        // 1. 将查询向量化
        float[] queryVector = embeddingService.embed(request.getQuery());

        if (queryVector == null) {
            log.warn("向量化失败，使用关键词搜索降级");
            return fallbackKeywordSearch(request, startTime);
        }

        // 2. 使用 pgvector 进行向量检索
        List<SearchResult.ResultItem> items = vectorSearch(queryVector, request);

        long processingTime = System.currentTimeMillis() - startTime;
        log.debug("向量检索完成: 结果数={}, 耗时={}ms", items.size(), processingTime);

        return SearchResult.builder()
                .items(items)
                .total(items.size())
                .processingTimeMs(processingTime)
                .build();
    }

    /**
     * 使用 pgvector 进行向量检索
     */
    private List<SearchResult.ResultItem> vectorSearch(float[] queryVector, SearchRequest request) {
        try {
            // 将向量转换为字符串格式
            String vectorStr = arrayToVectorString(queryVector);

            // 构建权限过滤条件
            String sensitivityFilter = buildSensitivityFilter(request.getSensitivityLevels());

            // 使用 pgvector 的余弦相似度搜索
            String sql = """
                SELECT c.id, c.doc_id, c.chunk_index, c.text, c.section_title,
                       c.metadata,
                       1 - (c.embedding <=> ?::vector) as score
                FROM chunks c
                JOIN documents d ON c.doc_id = d.id
                WHERE d.owner_id = ?
                  AND d.status = 'PUBLISHED'
                  %s
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """.formatted(sensitivityFilter);

            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    sql, vectorStr, request.getUserId(), vectorStr, request.getTopK()
            );

            // 获取文档标题
            Set<Long> docIds = results.stream()
                    .map(r -> ((Number) r.get("doc_id")).longValue())
                    .collect(Collectors.toSet());

            Map<Long, String> docTitles = getDocumentTitles(docIds);

            return results.stream()
                    .filter(r -> ((Number) r.get("score")).doubleValue() >= request.getScoreThreshold())
                    .map(r -> {
                        long docId = ((Number) r.get("doc_id")).longValue();
                        return SearchResult.ResultItem.builder()
                                .chunkId(((Number) r.get("id")).longValue())
                                .docId(docId)
                                .chunkIndex((Integer) r.get("chunk_index"))
                                .content((String) r.get("text"))
                                .sectionTitle((String) r.get("section_title"))
                                .score(((Number) r.get("score")).doubleValue())
                                .docTitle(docTitles.getOrDefault(docId, "未知文档"))
                                .metadata(parseMetadata(r.get("metadata")))
                                .build();
                    })
                    .toList();

        } catch (Exception e) {
            log.error("向量检索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 关键词搜索降级方案
     */
    private SearchResult fallbackKeywordSearch(SearchRequest request, long startTime) {
        log.info("使用关键词搜索降级方案");

        List<Chunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<Chunk>()
                        .like(Chunk::getText, request.getQuery())
                        .last("LIMIT " + request.getTopK())
        );

        Set<Long> docIds = chunks.stream().map(Chunk::getDocId).collect(Collectors.toSet());
        Map<Long, String> docTitles = getDocumentTitles(docIds);

        List<SearchResult.ResultItem> items = chunks.stream()
                .map(chunk -> SearchResult.ResultItem.builder()
                        .chunkId(chunk.getId())
                        .docId(chunk.getDocId())
                        .chunkIndex(chunk.getChunkIndex())
                        .content(chunk.getText())
                        .sectionTitle(chunk.getSectionTitle())
                        .score(0.5)  // 关键词匹配给固定分数
                        .docTitle(docTitles.getOrDefault(chunk.getDocId(), "未知文档"))
                        .metadata(chunk.getMetadata())
                        .build())
                .toList();

        return SearchResult.builder()
                .items(items)
                .total(items.size())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * 构建敏感度过滤条件
     */
    private String buildSensitivityFilter(List<String> sensitivityLevels) {
        if (sensitivityLevels == null || sensitivityLevels.isEmpty()) {
            return "AND d.sensitivity IN ('PUBLIC', 'INTERNAL')";
        }
        String levels = sensitivityLevels.stream()
                .map(s -> "'" + s + "'")
                .collect(Collectors.joining(","));
        return "AND d.sensitivity IN (" + levels + ")";
    }

    /**
     * 获取文档标题映射
     */
    private Map<Long, String> getDocumentTitles(Set<Long> docIds) {
        if (docIds.isEmpty()) {
            return Map.of();
        }

        List<Document> docs = documentMapper.selectBatchIds(docIds);
        return docs.stream()
                .collect(Collectors.toMap(Document::getId, Document::getTitle));
    }

    /**
     * 将 float 数组转换为 PostgreSQL vector 字符串
     */
    private String arrayToVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.6f", vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, Object> parseMetadata(Object value) {
        if (value == null) {
            return Map.of();
        }

        String json;
        if (value instanceof PGobject pgObject) {
            json = pgObject.getValue();
        } else {
            json = value.toString();
        }

        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析 metadata 失败，内容：{}", json);
            return Map.of();
        }
    }
}
