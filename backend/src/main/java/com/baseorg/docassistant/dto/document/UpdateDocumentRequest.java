package com.baseorg.docassistant.dto.document;

import lombok.Data;

/**
 * 更新文档请求
 */
@Data
public class UpdateDocumentRequest {

    private String title;

    private String contentMarkdown;

    private Integer version;
}
