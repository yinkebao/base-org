package com.baseorg.docassistant.dto.importtask;

import com.baseorg.docassistant.entity.ImportTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 导入任务响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTaskResponse {

    private String taskId;
    private String filename;
    private String fileType;
    private Long fileSize;
    private String status;
    private Integer progress;
    private Integer totalChunks;
    private Integer processedChunks;
    private Long resultDocId;
    private String errorMessage;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static ImportTaskResponse fromEntity(ImportTask task) {
        return ImportTaskResponse.builder()
                .taskId(task.getTaskId())
                .filename(task.getFilename())
                .fileType(task.getFileType())
                .fileSize(task.getFileSize())
                .status(task.getStatus().name())
                .progress(task.getProgress())
                .totalChunks(task.getTotalChunks())
                .processedChunks(task.getProcessedChunks())
                .resultDocId(task.getResultDocId())
                .errorMessage(task.getErrorMessage())
                .metadata(task.getMetadata())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }
}
