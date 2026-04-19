package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.entity.QAMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文组装。
 * <p>
 * 本组件只负责 <b>历史对话</b> 与 <b>知识库检索片段</b> 两个维度的文本化。
 * 工具证据由 {@link com.baseorg.docassistant.service.qa.tool.EvidenceAssembler} 独立组装，
 * 上层编排（QAService）分别取用各自片段后统一拼接，避免同一批 reranked 结果被组装两次。
 */
@Service
public class ContextAssembler {

    private static final int HISTORY_LOOKBACK = 6;
    private static final int HISTORY_CONTENT_LIMIT = 180;
    private static final int RETRIEVAL_CONTENT_LIMIT = 600;
    private static final int RETRIEVAL_TOKEN_BUDGET = 3200;

    /**
     * 组装历史对话 + 知识库检索的完整上下文。保留以兼容既有调用方。
     */
    public String assemble(List<QAMessage> recentMessages, List<SearchResult.ResultItem> items) {
        List<String> sections = new ArrayList<>();
        String history = assembleHistory(recentMessages);
        if (!history.isBlank()) {
            sections.add(history);
        }
        String retrieval = assembleRetrieval(items);
        if (!retrieval.isBlank()) {
            sections.add(retrieval);
        }
        return String.join("\n---\n", sections);
    }

    /**
     * 仅组装历史对话片段，便于上层按需拼接。
     */
    public String assembleHistory(List<QAMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "";
        }
        String history = recentMessages.stream()
                .skip(Math.max(recentMessages.size() - HISTORY_LOOKBACK, 0))
                .map(message -> "%s: %s".formatted(message.getRole().name(), truncate(message.getContent(), HISTORY_CONTENT_LIMIT)))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return history.isBlank() ? "" : "【历史对话】\n" + history;
    }

    /**
     * 仅组装知识库检索片段，预算内按序塞入。
     */
    public String assembleRetrieval(List<SearchResult.ResultItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder docSection = new StringBuilder("【知识库片段】\n");
        boolean anyAppended = false;
        for (SearchResult.ResultItem item : items) {
            String block = """
                    标题：%s
                    小节：%s
                    内容：%s

                    """.formatted(item.getDocTitle(),
                    item.getSectionTitle() == null ? "-" : item.getSectionTitle(),
                    truncate(item.getContent(), RETRIEVAL_CONTENT_LIMIT));
            if (docSection.length() + block.length() > RETRIEVAL_TOKEN_BUDGET) {
                break;
            }
            docSection.append(block);
            anyAppended = true;
        }
        return anyAppended ? docSection.toString() : "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
