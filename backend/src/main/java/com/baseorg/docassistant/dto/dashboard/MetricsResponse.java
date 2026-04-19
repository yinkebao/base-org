package com.baseorg.docassistant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 系统指标响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse {

    // 文档统计
    private DocumentMetrics documents;

    // 导入统计
    private ImportMetrics imports;

    // 存储统计
    private StorageMetrics storage;

    // 系统资源
    private SystemMetrics system;

    // 时间戳
    private long timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentMetrics {
        private long totalDocuments;
        private long publishedDocuments;
        private long draftDocuments;
        private long totalChunks;
        private long weeklyGrowth;
        private Map<String, Long> byType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportMetrics {
        private long totalTasks;
        private long completedTasks;
        private long processingTasks;
        private long failedTasks;
        private double successRate;
        private double avgProcessingTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageMetrics {
        private long usedSpace;
        private long totalSpace;
        private double usagePercent;
        private String usedSpaceFormatted;
        private String totalSpaceFormatted;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemMetrics {
        private double cpuUsage;
        private double memoryUsage;
        private long totalMemory;
        private long usedMemory;
        private long freeMemory;
        private String javaVersion;
        private String osName;
        private long uptime;
    }
}
