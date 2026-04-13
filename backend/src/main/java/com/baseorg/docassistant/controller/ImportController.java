package com.baseorg.docassistant.controller;

import com.baseorg.docassistant.dto.ApiResponse;
import com.baseorg.docassistant.dto.importtask.CreateImportTaskRequest;
import com.baseorg.docassistant.dto.importtask.ImportTaskResponse;
import com.baseorg.docassistant.dto.importtask.RecentImportsResponse;
import jakarta.validation.Valid;
import com.baseorg.docassistant.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 文档导入控制器
 */
@Tag(name = "文档导入", description = "文档导入任务管理接口")
@RestController
@RequestMapping("/api/v1/documents/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /**
     * 创建导入任务
     */
    @Operation(summary = "创建导入任务", description = "上传文档并创建导入任务")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportTaskResponse> createTask(
            @Valid @ModelAttribute CreateImportTaskRequest request,
            @AuthenticationPrincipal Long userId) {
        ImportTaskResponse response = importService.createTask(request, userId);
        return ApiResponse.success(response);
    }

    /**
     * 获取任务状态
     */
    @Operation(summary = "获取任务状态", description = "查询导入任务的处理状态和进度")
    @GetMapping("/{taskId}")
    public ApiResponse<ImportTaskResponse> getTaskStatus(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @AuthenticationPrincipal Long userId) {

        ImportTaskResponse response = importService.getTaskStatus(taskId, userId);
        return ApiResponse.success(response);
    }

    /**
     * 取消任务
     */
    @Operation(summary = "取消任务", description = "取消待处理的导入任务")
    @PostMapping("/{taskId}/cancel")
    public ApiResponse<ImportTaskResponse> cancelTask(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @AuthenticationPrincipal Long userId) {

        ImportTaskResponse response = importService.cancelTask(taskId, userId);
        return ApiResponse.success(response);
    }

    /**
     * 获取最近导入任务
     */
    @Operation(summary = "最近导入", description = "获取用户最近的导入任务列表")
    @GetMapping("/recent")
    public ApiResponse<RecentImportsResponse> getRecentImports(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Long userId) {

        RecentImportsResponse response = importService.getRecentImports(userId, page, size);
        return ApiResponse.success(response);
    }
}
