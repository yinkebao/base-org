package com.baseorg.docassistant.dto.qa;

import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.ToolTraceItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 问答响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QAResponse {

    /**
     * 生成的答案
     */
    private String answer;

    /**
     * 答案置信度
     */
    private double confidence;

    /**
     * 来源文档片段
     */
    private List<SourceChunk> sources;

    /**
     * 是否使用降级回答
     */
    private boolean degraded;

    /**
     * 降级原因
     */
    private String degradeReason;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;

    /**
     * Prompt 哈希
     */
    private String promptHash;

    /**
     * 会话 ID
     */
    private Long sessionId;

    /**
     * 助手消息 ID
     */
    private Long messageId;

    /**
     * 改写后的查询
     */
    private String rewrittenQuery;

    /**
     * 意图类型
     */
    private String intent;

    /**
     * 兜底模式
     */
    private String fallbackMode;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 工具规划摘要
     */
    private String planSummary;

    /**
     * 工具执行轨迹
     */
    private List<ToolTraceItem> toolTrace;

    /**
     * Mermaid 图表
     */
    private List<DiagramPayload> diagrams;

    /**
     * 外部搜索来源
     */
    private List<ToolEvidence> externalSources;

    /**
     * 来源文档片段
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceChunk {
        private Long chunkId;
        private Long docId;
        private String docTitle;
        private String content;
        private double score;
        private int chunkIndex;
        private Map<String, Object> metadata;
    }
}
