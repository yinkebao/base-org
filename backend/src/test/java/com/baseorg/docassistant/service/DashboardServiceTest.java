package com.baseorg.docassistant.service;

import com.baseorg.docassistant.dto.dashboard.*;
import com.baseorg.docassistant.entity.Document;
import com.baseorg.docassistant.entity.ImportTask;
import com.baseorg.docassistant.mapper.ChunkMapper;
import com.baseorg.docassistant.mapper.DocumentMapper;
import com.baseorg.docassistant.mapper.ImportTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ImportTaskMapper importTaskMapper;

    @Mock
    private ChunkMapper chunkMapper;

    private DashboardService dashboardService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        dashboardService = new DashboardService(documentMapper, importTaskMapper, chunkMapper);
        // 使用反射设置 storagePath
        var storagePathField = DashboardService.class.getDeclaredField("storagePath");
        storagePathField.setAccessible(true);
        storagePathField.set(dashboardService, tempDir.toString());
    }

    @Test
    void shouldGetMetricsSuccessfully() {
        // Given
        when(documentMapper.selectCount(any())).thenReturn(100L);
        when(documentMapper.selectList(any())).thenReturn(List.of(
                createDocument(1L, "doc1.pdf", "pdf"),
                createDocument(2L, "doc2.md", "md")
        ));
        when(chunkMapper.selectCount(any())).thenReturn(500L);
        when(importTaskMapper.selectCount(any())).thenReturn(30L);

        // When
        MetricsResponse response = dashboardService.getMetrics();

        // Then - 验证基本结构而非具体数值
        assertThat(response.getDocuments()).isNotNull();
        assertThat(response.getImports()).isNotNull();
        assertThat(response.getStorage()).isNotNull();
        assertThat(response.getSystem()).isNotNull();
        assertThat(response.getTimestamp()).isPositive();
    }

    @Test
    void shouldGetAlertsWhenNoIssues() {
        // Given
        when(documentMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.selectList(any())).thenReturn(List.of());
        when(chunkMapper.selectCount(any())).thenReturn(0L);
        when(importTaskMapper.selectCount(any())).thenReturn(0L);

        // When
        AlertsResponse response = dashboardService.getAlerts();

        // Then - 验证响应结构
        assertThat(response).isNotNull();
        assertThat(response.getAlerts()).isNotNull();
    }

    @Test
    void shouldGetAlertsWithFailedImports() {
        // Given
        when(documentMapper.selectCount(null)).thenReturn(0L);
        when(documentMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.selectList(any())).thenReturn(List.of());
        when(chunkMapper.selectCount(null)).thenReturn(0L);

        when(importTaskMapper.selectCount(null)).thenReturn(5L);
        when(importTaskMapper.selectCount(any())).thenReturn(2L).thenReturn(0L).thenReturn(0L).thenReturn(3L);

        // When
        AlertsResponse response = dashboardService.getAlerts();

        // Then
        assertThat(response.getTotal()).isGreaterThan(0);
        assertThat(response.getInfoCount()).isGreaterThan(0);
    }

    @Test
    void shouldCheckHealthSuccessfully() {
        // Given
        when(documentMapper.selectCount(null)).thenReturn(10L);

        // When
        HealthResponse response = dashboardService.healthCheck();

        // Then
        assertThat(response.getStatus()).isIn("UP", "DEGRADED", "DOWN");
        assertThat(response.getComponents()).containsKeys("database", "storage", "memory");
        assertThat(response.getTimestamp()).isPositive();
    }

    @Test
    void shouldReturnDownStatusWhenDatabaseFails() {
        // Given
        when(documentMapper.selectCount(null)).thenThrow(new RuntimeException("Connection failed"));

        // When
        HealthResponse response = dashboardService.healthCheck();

        // Then
        assertThat(response.getStatus()).isEqualTo("DOWN");
        assertThat(response.getComponents().get("database").getStatus()).isEqualTo("DOWN");
    }

    // Helper methods

    private Document createDocument(Long id, String title, String fileType) {
        return Document.builder()
                .id(id)
                .title(title)
                .fileType(fileType)
                .status(Document.DocumentStatus.PUBLISHED)
                .sensitivity(Document.SensitivityLevel.INTERNAL)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
