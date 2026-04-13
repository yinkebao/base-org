package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 抓取后的网页正文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchedWebPage {
    private String url;
    private String domain;
    private String title;
    private String publishedAt;
    private String content;
    private boolean suspicious;
    private String suspiciousReason;
}
