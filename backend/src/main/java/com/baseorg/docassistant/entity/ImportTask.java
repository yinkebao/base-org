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
 * 导入任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "import_tasks", autoResultMap = true)
public class ImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private String taskId;

    @TableField("owner_id")
    private Long ownerId;

    @TableField("filename")
    private String filename;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_path")
    private String filePath;

    @TableField("status")
    private TaskStatus status;

    @TableField("progress")
    private Integer progress;

    @TableField("total_chunks")
    private Integer totalChunks;

    @TableField("processed_chunks")
    private Integer processedChunks;

    @TableField("result_doc_id")
    private Long resultDocId;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    /**
     * 任务状态
     */
    public enum TaskStatus {
        PENDING,        // 等待处理
        PARSING,        // 解析中
        CHUNKING,       // 分块中
        EMBEDDING,      // 向量化中
        STORING,        // 存储中
        COMPLETED,      // 已完成
        FAILED,         // 失败
        CANCELLED       // 已取消
    }
}
