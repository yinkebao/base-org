package com.baseorg.docassistant.controller.admin;

import com.baseorg.docassistant.dto.ApiResponse;
import com.baseorg.docassistant.dto.dashboard.AlertsResponse;
import com.baseorg.docassistant.dto.dashboard.HealthResponse;
import com.baseorg.docassistant.dto.dashboard.MetricsResponse;
import com.baseorg.docassistant.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘与管理控制器
 */
@Tag(name = "仪表盘", description = "系统监控与管理接口")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取系统指标
     */
    @Operation(summary = "获取系统指标", description = "获取文档统计、导入统计、存储和系统资源指标")
    @GetMapping("/metrics")
    public ApiResponse<MetricsResponse> getMetrics() {
        MetricsResponse response = dashboardService.getMetrics();
        return ApiResponse.success(response);
    }

    /**
     * 获取系统告警
     */
    @Operation(summary = "获取系统告警", description = "获取存储、系统资源、导入任务等告警信息")
    @GetMapping("/alerts")
    public ApiResponse<AlertsResponse> getAlerts() {
        AlertsResponse response = dashboardService.getAlerts();
        return ApiResponse.success(response);
    }

    /**
     * 系统健康检查
     */
    @Operation(summary = "健康检查", description = "检查数据库、存储、内存等组件健康状态")
    @GetMapping("/health")
    public HealthResponse healthCheck() {
        return dashboardService.healthCheck();
    }
}
