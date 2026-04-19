package com.baseorg.docassistant.dto.qa.tool;

/**
 * 工具与 RAG 的组合策略。
 */
public enum ToolExecutionStrategy {
    RAG_ONLY,
    TOOL_ONLY,
    TOOL_THEN_RAG,
    RAG_THEN_TOOL_FALLBACK
}
