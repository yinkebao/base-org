package com.baseorg.docassistant.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档图片上传响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentImageUploadResponse {

    private String url;

    private String alt;

    private String filename;

    private Long size;
}
