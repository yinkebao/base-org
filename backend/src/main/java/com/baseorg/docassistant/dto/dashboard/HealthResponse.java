package com.baseorg.docassistant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 系统健康检查响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthResponse {

    private String status;              // UP, DOWN, DEGRADED
    private long timestamp;
    private Map<String, ComponentHealth> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentHealth {
        private String status;          // UP, DOWN
        private String message;
        private Map<String, Object> details;
    }
}
