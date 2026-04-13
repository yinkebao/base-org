package com.baseorg.docassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("documents")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("file_path")
    private String filePath;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("status")
    private DocumentStatus status;

    @TableField("sensitivity")
    private SensitivityLevel sensitivity;

    @TableField("owner_id")
    private Long ownerId;

    @TableField("dept_id")
    private Long deptId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("version")
    private Integer version;

    @TableField("metadata")
    private String metadata;

    @TableField("content_markdown")
    private String contentMarkdown;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 文档状态
     */
    public enum DocumentStatus {
        DRAFT,      // 编辑中
        PENDING,    // 待审核
        PUBLISHED,  // 已发布
        ARCHIVED    // 已归档
    }

    /**
     * 敏感度级别
     */
    public enum SensitivityLevel {
        PUBLIC,         // 公开
        INTERNAL,       // 内部
        CONFIDENTIAL,   // 机密
        SECRET          // 绝密
    }
}
