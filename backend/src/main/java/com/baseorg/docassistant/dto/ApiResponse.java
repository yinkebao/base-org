package com.baseorg.docassistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 统一API响应格式
 * 与前端Mock接口格式对齐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean ok;
    private T data;
    private String code;
    private String message;
    private String traceId;

    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .ok(true)
                .data(data)
                .traceId(generateTraceId())
                .build();
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    /**
     * 失败响应
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .ok(false)
                .code(code)
                .message(message)
                .traceId(generateTraceId())
                .build();
    }

    /**
     * 失败响应（带详情）
     */
    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return ApiResponse.<T>builder()
                .ok(false)
                .code(code)
                .message(message)
                .data((T) details)
                .traceId(generateTraceId())
                .build();
    }

    private static String generateTraceId() {
        return "trace_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
