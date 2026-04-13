package com.baseorg.docassistant.dto.document;

import com.baseorg.docassistant.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档详情响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDetailResponse {

    private Long id;
    private String title;
    private String description;
    private String sourceUrl;
    private String fileType;
    private Long fileSize;
    private String status;
    private String sensitivity;
    private Long ownerId;
    private String ownerName;
    private Long deptId;
    private Long parentId;
    private Integer version;
    private String metadata;
    private String contentMarkdown;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 统计信息
    private Integer chunkCount;

    public static DocumentDetailResponse fromEntity(Document doc) {
        return DocumentDetailResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .description(doc.getDescription())
                .sourceUrl(doc.getSourceUrl())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus().name())
                .sensitivity(doc.getSensitivity().name())
                .ownerId(doc.getOwnerId())
                .parentId(doc.getParentId())
                .version(doc.getVersion())
                .metadata(doc.getMetadata())
                .contentMarkdown(doc.getContentMarkdown())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
