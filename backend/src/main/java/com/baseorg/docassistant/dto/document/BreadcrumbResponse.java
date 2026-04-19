package com.baseorg.docassistant.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面包屑导航响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreadcrumbResponse {

    private List<BreadcrumbItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreadcrumbItem {
        private Long id;
        private String title;
        private String type;
    }
}
