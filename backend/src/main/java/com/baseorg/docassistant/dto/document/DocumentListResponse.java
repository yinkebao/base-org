package com.baseorg.docassistant.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档列表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentListResponse {

    private List<DocumentListItem> documents;
    private long total;
    private int page;
    private int size;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentListItem {
        private Long id;
        private String title;
        private String fileType;
        private String status;
        private String sensitivity;
        private Long parentId;
        private Long fileSize;
        private String ownerName;
        private String createdAt;
        private String updatedAt;
    }
}
