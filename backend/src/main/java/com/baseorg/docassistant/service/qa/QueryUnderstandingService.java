package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.entity.QAMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询理解与改写
 */
@Service
public class QueryUnderstandingService {

    private static final List<String> SMALL_TALK_PATTERNS = List.of(
            "hi", "hello", "hey", "嗨", "你好", "您好", "在吗", "早上好", "上午好", "下午好", "晚上好"
    );

    public QueryPlan plan(String rawQuestion, List<QAMessage> recentMessages) {
        String normalized = normalize(rawQuestion);
        String historySummary = summarizeHistory(recentMessages);
        QueryPlan.Intent intent = detectIntent(normalized);
        String rewritten = rewrite(normalized, recentMessages);

        return QueryPlan.builder()
                .intent(intent)
                .rawQuestion(normalized)
                .rewrittenQuery(rewritten)
                .historySummary(historySummary)
                .shouldSkipRetrieval(intent == QueryPlan.Intent.SMALL_TALK)
                .toolPlanned(false)
                .graphPlanned(false)
                .diagramRequested(isDiagramRequested(normalized))
                .diagramTypeHint(resolveDiagramTypeHint(normalized))
                .toolHint(resolveToolHint(normalized))
                .build();
    }

    private String normalize(String question) {
        return question == null ? "" : question.trim()
                .replaceAll("[!！?？,，.。]+$", "")
                .replaceAll("\\s+", " ");
    }

    private String summarizeHistory(List<QAMessage> recentMessages) {
        return recentMessages.stream()
                .skip(Math.max(recentMessages.size() - 6, 0))
                .map(message -> "%s: %s".formatted(message.getRole().name(), clip(message.getContent(), 120)))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String rewrite(String question, List<QAMessage> recentMessages) {
        if (question.isBlank() || recentMessages.isEmpty()) {
            return question;
        }

        String lower = question.toLowerCase();
        boolean contextDependent = lower.contains("这个")
                || lower.contains("那个")
                || lower.contains("它")
                || lower.contains("继续")
                || lower.contains("上述")
                || lower.contains("上面");

        if (!contextDependent) {
            return question;
        }

        QAMessage latestUser = null;
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            QAMessage message = recentMessages.get(i);
            if (message.getRole() == QAMessage.MessageRole.USER) {
                latestUser = message;
                break;
            }
        }
        if (latestUser == null) {
            return question;
        }
        return "结合上一轮问题“%s”，当前问题是：%s".formatted(clip(latestUser.getContent(), 120), question);
    }

    private QueryPlan.Intent detectIntent(String question) {
        if (question == null || question.isBlank()) {
            return QueryPlan.Intent.RAG_SEARCH;
        }
        String lower = question.toLowerCase();
        boolean isSmallTalk = SMALL_TALK_PATTERNS.stream().anyMatch(pattern -> lower.equals(pattern) || lower.startsWith(pattern + " "));
        return isSmallTalk ? QueryPlan.Intent.SMALL_TALK : QueryPlan.Intent.RAG_SEARCH;
    }

    private boolean isDiagramRequested(String question) {
        return question != null && (question.contains("流程图") || question.contains("时序图") || question.toLowerCase().contains("mermaid"));
    }

    private String resolveDiagramTypeHint(String question) {
        if (question == null) {
            return null;
        }
        String lower = question.toLowerCase();
        if (lower.contains("时序图") || lower.contains("sequence")) {
            return "sequence";
        }
        if (lower.contains("流程图") || lower.contains("mermaid")) {
            return "flowchart";
        }
        return null;
    }

    private String resolveToolHint(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String lower = question.toLowerCase();
        if (lower.contains("飞书")) {
            return "feishu";
        }
        if (lower.contains("蓝湖")) {
            return "lanhu";
        }
        if (lower.contains("联网搜索") || lower.contains("web search") || lower.contains("搜索最新")) {
            return "web_search";
        }
        return null;
    }

    private String clip(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
