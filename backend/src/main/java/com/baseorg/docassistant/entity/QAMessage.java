package com.baseorg.docassistant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baseorg.docassistant.config.typehandler.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * QA 消息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "qa_messages", autoResultMap = true)
public class QAMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("user_id")
    private Long userId;

    @TableField("role")
    private MessageRole role;

    @TableField("content")
    private String content;

    @TableField("rewritten_query")
    private String rewrittenQuery;

    @TableField("intent")
    private String intent;

    @TableField(value = "sources_json", typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> sourcesJson;

    @TableField("confidence")
    private Double confidence;

    @TableField("prompt_hash")
    private String promptHash;

    @TableField("status")
    private MessageStatus status;

    @TableField("degraded")
    private Boolean degraded;

    @TableField("degrade_reason")
    private String degradeReason;

    @TableField("fallback_mode")
    private String fallbackMode;

    @TableField("error_code")
    private String errorCode;

    @TableField("processing_time_ms")
    private Long processingTimeMs;

    @TableField("plan_summary")
    private String planSummary;

    @TableField(value = "tool_trace_json", typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> toolTraceJson;

    @TableField(value = "diagrams_json", typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> diagramsJson;

    @TableField(value = "external_sources_json", typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> externalSourcesJson;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum MessageRole {
        USER,
        ASSISTANT
    }

    public enum MessageStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
