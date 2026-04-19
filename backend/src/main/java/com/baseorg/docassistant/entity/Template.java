package com.baseorg.docassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baseorg.docassistant.config.typehandler.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模板实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "templates", autoResultMap = true)
public class Template {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category;

    @TableField("content")
    private String content;

    @TableField(value = "variables", typeHandler = JsonbTypeHandler.class)
    private List<TemplateVariable> variables;

    @TableField(value = "example_values", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> exampleValues;

    @TableField(value = "constraints", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> constraints;

    @TableField("version")
    private Integer version;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 模板分类
     */
    public enum Category {
        REQUIREMENT,    // 需求文档
        TEST_CASE,      // 测试用例
        API_DOC,        // API文档
        DESIGN_DOC,     // 设计文档
        REPORT          // 报告
    }

    /**
     * 模板变量定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateVariable {
        private String name;
        private String type;        // string, text, number, boolean, date, list
        private Boolean required;
        private String description;
        private Object defaultValue;
    }
}
