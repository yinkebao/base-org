package com.baseorg.docassistant.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * 文档图片资源
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentImageResource {

    private Resource resource;

    private MediaType mediaType;
}
