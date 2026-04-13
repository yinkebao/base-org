package com.baseorg.docassistant.dto.document;

import com.baseorg.docassistant.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档树节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTreeNode {

    private Long id;
    private String title;
    private String fileType;
    private String status;
    private String sensitivity;
    private boolean isFolder;
    private Long parentId;
    private List<DocumentTreeNode> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentTreeNode fromEntity(Document doc, List<DocumentTreeNode> children) {
        return DocumentTreeNode.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .fileType(doc.getFileType())
                .status(doc.getStatus().name())
                .sensitivity(doc.getSensitivity().name())
                .isFolder(doc.getFileType() == null)
                .parentId(doc.getParentId())
                .children(children)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
