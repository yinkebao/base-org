package com.baseorg.docassistant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统告警响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertsResponse {

    private List<AlertItem> alerts;
    private long total;
    private int criticalCount;
    private int warningCount;
    private int infoCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertItem {
        private String id;
        private String type;        // CRITICAL, WARNING, INFO
        private String category;    // STORAGE, SYSTEM, IMPORT, SECURITY
        private String title;
        private String message;
        private String source;
        private LocalDateTime timestamp;
        private boolean acknowledged;
        private Map<String, Object> metadata;
    }
}
