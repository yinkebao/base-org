package com.baseorg.docassistant.dto.qa;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问答请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QARequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题长度不能超过1000字符")
    private String question;

    /**
     * 可选的会话 ID
     */
    private Long sessionId;

    @Min(value = 1, message = "返回结果数量最少为1")
    @Max(value = 10, message = "返回结果数量最多为10")
    @Builder.Default
    private int topK = 5;

    @Min(value = 0, message = "相似度阈值在0-1之间")
    @Max(value = 1, message = "相似度阈值在0-1之间")
    @Builder.Default
    private double scoreThreshold = 0.7;

    /**
     * 是否返回来源文档
     */
    @Builder.Default
    private boolean includeSources = true;

    /**
     * 是否流式返回
     */
    @Builder.Default
    private boolean stream = false;

    /**
     * 是否为本次问题显式开启联网搜索
     */
    @Builder.Default
    private boolean webSearchEnabled = false;
}
