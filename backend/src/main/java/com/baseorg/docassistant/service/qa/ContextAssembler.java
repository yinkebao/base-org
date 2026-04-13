package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.entity.QAMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文组装
 */
@Service
public class ContextAssembler {

    public String assemble(List<QAMessage> recentMessages, List<SearchResult.ResultItem> items) {
        List<String> sections = new ArrayList<>();

        if (recentMessages != null && !recentMessages.isEmpty()) {
            String history = recentMessages.stream()
                    .skip(Math.max(recentMessages.size() - 6, 0))
                    .map(message -> "%s: %s".formatted(message.getRole().name(), truncate(message.getContent(), 180)))
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            if (!history.isBlank()) {
                sections.add("【历史对话】\n" + history);
            }
        }

        int budget = 3200;
        StringBuilder docSection = new StringBuilder("【知识库片段】\n");
        for (SearchResult.ResultItem item : items) {
            String block = """
                    标题：%s
                    小节：%s
                    内容：%s
                    
                    """.formatted(item.getDocTitle(),
                    item.getSectionTitle() == null ? "-" : item.getSectionTitle(),
                    truncate(item.getContent(), 600));
            if (docSection.length() + block.length() > budget) {
                break;
            }
            docSection.append(block);
        }
        sections.add(docSection.toString());
        return String.join("\n---\n", sections);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
