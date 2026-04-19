package com.baseorg.docassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baseorg.docassistant.config.typehandler.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "audit_logs", autoResultMap = true)
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("action")
    private String action;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private String resourceId;

    @TableField("prompt_hash")
    private String promptHash;

    @TableField("token_cost")
    private Integer tokenCost;

    @TableField(value = "details", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> details;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("user_agent")
    private String userAgent;

    @TableField("trace_id")
    private String traceId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 审计操作类型
     */
    public static class Action {
        public static final String QA_QUERY = "QA_QUERY";
        public static final String DOCUMENT_IMPORT = "DOCUMENT_IMPORT";
        public static final String TEMPLATE_CREATE = "TEMPLATE_CREATE";
        public static final String TEMPLATE_UPDATE = "TEMPLATE_UPDATE";
        public static final String USER_LOGIN = "USER_LOGIN";
        public static final String USER_LOGOUT = "USER_LOGOUT";
    }

    /**
     * 资源类型
     */
    public static class ResourceType {
        public static final String DOCUMENT = "DOCUMENT";
        public static final String CHUNK = "CHUNK";
        public static final String TEMPLATE = "TEMPLATE";
        public static final String USER = "USER";
        public static final String IMPORT_TASK = "IMPORT_TASK";
        public static final String QA_SESSION = "QA_SESSION";
    }
}
