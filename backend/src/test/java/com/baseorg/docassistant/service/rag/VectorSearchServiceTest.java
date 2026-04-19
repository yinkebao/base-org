package com.baseorg.docassistant.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baseorg.docassistant.dto.qa.SearchRequest;
import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.entity.Chunk;
import com.baseorg.docassistant.entity.Document;
import com.baseorg.docassistant.mapper.ChunkMapper;
import com.baseorg.docassistant.mapper.DocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VectorSearchServiceTest {

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private VectorSearchService vectorSearchService;

    @BeforeEach
    void setUp() {
        vectorSearchService = new VectorSearchService(chunkMapper, documentMapper, embeddingService, jdbcTemplate);
    }

    @Test
    void shouldReturnFallbackResultWhenEmbeddingFails() {
        // Given
        SearchRequest request = SearchRequest.builder()
                .query("测试查询")
                .userId(1L)
                .topK(5)
                .scoreThreshold(0.5)
                .build();

        when(embeddingService.embed(anyString())).thenReturn(null);
        when(chunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                createChunk(1L, 1L, "测试内容", "section1")
        ));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(
                Document.builder().id(1L).title("测试文档").build()
        ));

        // When
        SearchResult result = vectorSearchService.search(request);

        // Then
        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getProcessingTimeMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void shouldReturnEmptyResultWhenNoChunksFound() {
        // Given
        SearchRequest request = SearchRequest.builder()
                .query("未找到的查询")
                .userId(1L)
                .topK(5)
                .scoreThreshold(0.5)
                .build();

        when(embeddingService.embed(anyString())).thenReturn(null);
        when(chunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        // When
        SearchResult result = vectorSearchService.search(request);

        // Then
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void shouldIncludeDocTitleInSearchResult() {
        // Given
        SearchRequest request = SearchRequest.builder()
                .query("测试查询")
                .userId(1L)
                .topK(5)
                .scoreThreshold(0.5)
                .build();

        when(embeddingService.embed(anyString())).thenReturn(null);
        when(chunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                createChunk(1L, 100L, "测试内容", "section1")
        ));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(
                Document.builder().id(100L).title("重要文档").build()
        ));

        // When
        SearchResult result = vectorSearchService.search(request);

        // Then
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getDocTitle()).isEqualTo("重要文档");
    }

    @Test
    void shouldUseUnknownTitleWhenDocumentNotFound() {
        // Given
        SearchRequest request = SearchRequest.builder()
                .query("测试查询")
                .userId(1L)
                .topK(5)
                .scoreThreshold(0.5)
                .build();

        when(embeddingService.embed(anyString())).thenReturn(null);
        when(chunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                createChunk(1L, 100L, "测试内容", "section1")
        ));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of());

        // When
        SearchResult result = vectorSearchService.search(request);

        // Then
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getDocTitle()).isEqualTo("未知文档");
    }

    // Helper methods

    private Chunk createChunk(Long id, Long docId, String text, String sectionTitle) {
        return Chunk.builder()
                .id(id)
                .docId(docId)
                .chunkIndex(0)
                .text(text)
                .sectionTitle(sectionTitle)
                .build();
    }
}
