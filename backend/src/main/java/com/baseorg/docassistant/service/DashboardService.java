package com.baseorg.docassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baseorg.docassistant.dto.dashboard.*;
import com.baseorg.docassistant.entity.Document;
import com.baseorg.docassistant.entity.ImportTask;
import com.baseorg.docassistant.mapper.ChunkMapper;
import com.baseorg.docassistant.mapper.DocumentMapper;
import com.baseorg.docassistant.mapper.ImportTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 仪表盘服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DocumentMapper documentMapper;
    private final ImportTaskMapper importTaskMapper;
    private final ChunkMapper chunkMapper;

    @Value("${app.storage.local.path:${user.home}/docassist/storage}")
    private String storagePath;

    private static final long START_TIME = System.currentTimeMillis();

    /**
     * 获取系统指标
     */
    @Transactional(readOnly = true)
    public MetricsResponse getMetrics() {
        log.debug("获取系统指标");

        return MetricsResponse.builder()
                .documents(getDocumentMetrics())
                .imports(getImportMetrics())
                .storage(getStorageMetrics())
                .system(getSystemMetrics())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 获取系统告警
     */
    @Transactional(readOnly = true)
    public AlertsResponse getAlerts() {
        log.debug("获取系统告警");

        List<AlertsResponse.AlertItem> alerts = new ArrayList<>();

        // 检查存储告警
        checkStorageAlerts(alerts);

        // 检查系统资源告警
        checkSystemAlerts(alerts);

        // 检查导入任务告警
        checkImportAlerts(alerts);

        // 统计各级别数量
        int criticalCount = (int) alerts.stream().filter(a -> "CRITICAL".equals(a.getType())).count();
        int warningCount = (int) alerts.stream().filter(a -> "WARNING".equals(a.getType())).count();
        int infoCount = (int) alerts.stream().filter(a -> "INFO".equals(a.getType())).count();

        return AlertsResponse.builder()
                .alerts(alerts)
                .total(alerts.size())
                .criticalCount(criticalCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .build();
    }

    /**
     * 系统健康检查
     */
    public HealthResponse healthCheck() {
        log.debug("执行健康检查");

        Map<String, HealthResponse.ComponentHealth> components = new LinkedHashMap<>();

        // 检查数据库
        components.put("database", checkDatabaseHealth());

        // 检查存储
        components.put("storage", checkStorageHealth());

        // 检查内存
        components.put("memory", checkMemoryHealth());

        // 确定整体状态
        String status = determineOverallStatus(components);

        return HealthResponse.builder()
                .status(status)
                .timestamp(System.currentTimeMillis())
                .components(components)
                .build();
    }

    // ==================== 私有方法 ====================

    private MetricsResponse.DocumentMetrics getDocumentMetrics() {
        long total = documentMapper.selectCount(null);
        long published = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getStatus, Document.DocumentStatus.PUBLISHED));
        long draft = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getStatus, Document.DocumentStatus.DRAFT));

        // 本周新增
        LocalDateTime weekAgo = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
        long weeklyGrowth = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>()
                        .ge(Document::getCreatedAt, weekAgo));

        // 按类型统计
        Map<String, Long> byType = new HashMap<>();
        documentMapper.selectList(null).forEach(doc -> {
            String type = doc.getFileType() != null ? doc.getFileType() : "folder";
            byType.merge(type, 1L, Long::sum);
        });

        // 总块数（简化处理）
        long totalChunks = chunkMapper.selectCount(null);

        return MetricsResponse.DocumentMetrics.builder()
                .totalDocuments(total)
                .publishedDocuments(published)
                .draftDocuments(draft)
                .totalChunks(totalChunks)
                .weeklyGrowth(weeklyGrowth)
                .byType(byType)
                .build();
    }

    private MetricsResponse.ImportMetrics getImportMetrics() {
        long total = importTaskMapper.selectCount(null);
        long completed = importTaskMapper.selectCount(
                new LambdaQueryWrapper<ImportTask>()
                        .eq(ImportTask::getStatus, ImportTask.TaskStatus.COMPLETED));
        long failed = importTaskMapper.selectCount(
                new LambdaQueryWrapper<ImportTask>()
                        .eq(ImportTask::getStatus, ImportTask.TaskStatus.FAILED));
        long cancelled = importTaskMapper.selectCount(
                new LambdaQueryWrapper<ImportTask>()
                        .eq(ImportTask::getStatus, ImportTask.TaskStatus.CANCELLED));
        long processing = total - completed - failed - cancelled;

        double successRate = total > 0 ? (double) completed / total * 100 : 0;

        return MetricsResponse.ImportMetrics.builder()
                .totalTasks(total)
                .completedTasks(completed)
                .processingTasks(Math.max(processing, 0))
                .failedTasks(failed)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .avgProcessingTime(2.5)  // TODO: 实际计算
                .build();
    }

    private MetricsResponse.StorageMetrics getStorageMetrics() {
        File storageDir = new File(storagePath);
        long totalSpace = storageDir.getTotalSpace();
        long freeSpace = storageDir.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;
        double usagePercent = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;

        return MetricsResponse.StorageMetrics.builder()
                .usedSpace(usedSpace)
                .totalSpace(totalSpace)
                .usagePercent(Math.round(usagePercent * 100.0) / 100.0)
                .usedSpaceFormatted(formatSize(usedSpace))
                .totalSpaceFormatted(formatSize(totalSpace))
                .build();
    }

    private MetricsResponse.SystemMetrics getSystemMetrics() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        long totalMemory = memoryBean.getHeapMemoryUsage().getMax();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long freeMemory = totalMemory - usedMemory;
        double memoryUsage = totalMemory > 0 ? (double) usedMemory / totalMemory * 100 : 0;

        double cpuUsage = osBean.getSystemLoadAverage();
        if (cpuUsage < 0) cpuUsage = 0;

        long uptime = System.currentTimeMillis() - START_TIME;

        return MetricsResponse.SystemMetrics.builder()
                .cpuUsage(Math.round(cpuUsage * 100.0) / 100.0)
                .memoryUsage(Math.round(memoryUsage * 100.0) / 100.0)
                .totalMemory(totalMemory)
                .usedMemory(usedMemory)
                .freeMemory(freeMemory)
                .javaVersion(System.getProperty("java.version"))
                .osName(System.getProperty("os.name"))
                .uptime(uptime)
                .build();
    }

    private void checkStorageAlerts(List<AlertsResponse.AlertItem> alerts) {
        MetricsResponse.StorageMetrics storage = getStorageMetrics();

        if (storage.getUsagePercent() >= 90) {
            alerts.add(AlertsResponse.AlertItem.builder()
                    .id(UUID.randomUUID().toString())
                    .type("CRITICAL")
                    .category("STORAGE")
                    .title("存储空间严重不足")
                    .message(String.format("存储使用率已达 %.1f%%，请立即清理或扩容", storage.getUsagePercent()))
                    .source("storage-monitor")
                    .timestamp(LocalDateTime.now())
                    .acknowledged(false)
                    .metadata(Map.of("usagePercent", storage.getUsagePercent()))
                    .build());
        } else if (storage.getUsagePercent() >= 80) {
            alerts.add(AlertsResponse.AlertItem.builder()
                    .id(UUID.randomUUID().toString())
                    .type("WARNING")
                    .category("STORAGE")
                    .title("存储空间即将不足")
                    .message(String.format("存储使用率已达 %.1f%%", storage.getUsagePercent()))
                    .source("storage-monitor")
                    .timestamp(LocalDateTime.now())
                    .acknowledged(false)
                    .build());
        }
    }

    private void checkSystemAlerts(List<AlertsResponse.AlertItem> alerts) {
        MetricsResponse.SystemMetrics system = getSystemMetrics();

        if (system.getMemoryUsage() >= 90) {
            alerts.add(AlertsResponse.AlertItem.builder()
                    .id(UUID.randomUUID().toString())
                    .type("WARNING")
                    .category("SYSTEM")
                    .title("内存使用率过高")
                    .message(String.format("JVM 内存使用率已达 %.1f%%", system.getMemoryUsage()))
                    .source("system-monitor")
                    .timestamp(LocalDateTime.now())
                    .acknowledged(false)
                    .build());
        }
    }

    private void checkImportAlerts(List<AlertsResponse.AlertItem> alerts) {
        long failedCount = importTaskMapper.selectCount(
                new LambdaQueryWrapper<ImportTask>()
                        .eq(ImportTask::getStatus, ImportTask.TaskStatus.FAILED));

        if (failedCount > 0) {
            alerts.add(AlertsResponse.AlertItem.builder()
                    .id(UUID.randomUUID().toString())
                    .type("INFO")
                    .category("IMPORT")
                    .title("存在失败的导入任务")
                    .message(String.format("共有 %d 个导入任务失败", failedCount))
                    .source("import-monitor")
                    .timestamp(LocalDateTime.now())
                    .acknowledged(false)
                    .metadata(Map.of("failedCount", failedCount))
                    .build());
        }
    }

    private HealthResponse.ComponentHealth checkDatabaseHealth() {
        try {
            // 简单查询测试数据库连接
            documentMapper.selectCount(null);
            return HealthResponse.ComponentHealth.builder()
                    .status("UP")
                    .message("Database connection is healthy")
                    .build();
        } catch (Exception e) {
            log.error("数据库健康检查失败", e);
            return HealthResponse.ComponentHealth.builder()
                    .status("DOWN")
                    .message("Database connection failed: " + e.getMessage())
                    .build();
        }
    }

    private HealthResponse.ComponentHealth checkStorageHealth() {
        try {
            File storageDir = new File(storagePath);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            if (storageDir.canRead() && storageDir.canWrite()) {
                return HealthResponse.ComponentHealth.builder()
                        .status("UP")
                        .message("Storage is accessible")
                        .details(Map.of(
                                "path", storagePath,
                                "freeSpace", formatSize(storageDir.getFreeSpace())
                        ))
                        .build();
            } else {
                return HealthResponse.ComponentHealth.builder()
                        .status("DOWN")
                        .message("Storage is not accessible")
                        .build();
            }
        } catch (Exception e) {
            log.error("存储健康检查失败", e);
            return HealthResponse.ComponentHealth.builder()
                    .status("DOWN")
                    .message("Storage check failed: " + e.getMessage())
                    .build();
        }
    }

    private HealthResponse.ComponentHealth checkMemoryHealth() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        double usagePercent = maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;

        String status = usagePercent >= 95 ? "DOWN" : (usagePercent >= 85 ? "DEGRADED" : "UP");

        return HealthResponse.ComponentHealth.builder()
                .status(status)
                .message(String.format("Memory usage: %.1f%%", usagePercent))
                .details(Map.of(
                        "maxMemory", formatSize(maxMemory),
                        "usedMemory", formatSize(usedMemory),
                        "usagePercent", Math.round(usagePercent * 100.0) / 100.0
                ))
                .build();
    }

    private String determineOverallStatus(Map<String, HealthResponse.ComponentHealth> components) {
        boolean anyDown = components.values().stream()
                .anyMatch(c -> "DOWN".equals(c.getStatus()));
        boolean anyDegraded = components.values().stream()
                .anyMatch(c -> "DEGRADED".equals(c.getStatus()));

        if (anyDown) return "DOWN";
        if (anyDegraded) return "DEGRADED";
        return "UP";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}
