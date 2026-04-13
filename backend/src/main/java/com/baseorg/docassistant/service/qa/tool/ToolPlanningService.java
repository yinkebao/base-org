package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaToolProperties;
import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionPhase;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionStrategy;
import com.baseorg.docassistant.dto.qa.tool.ToolPlan;
import com.baseorg.docassistant.dto.qa.tool.ToolStep;
import com.baseorg.docassistant.dto.qa.tool.ToolType;
import com.baseorg.docassistant.entity.QAMessage;
import com.baseorg.docassistant.service.rag.LLMService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工具规划服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolPlanningService {

    private final QAToolRegistry toolRegistry;
    private final AppQaToolProperties toolProperties;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public ToolPlan plan(QueryPlan queryPlan, List<QAMessage> recentMessages, boolean webSearchEnabled) {
        List<ToolDescriptor> enabledTools = toolRegistry.listEnabledDescriptors();
        if (queryPlan == null || queryPlan.getIntent() == QueryPlan.Intent.SMALL_TALK || enabledTools.isEmpty()) {
            log.info("工具规划跳过: intent={}, enabledToolCount={}, reason={}",
                    queryPlan == null ? null : queryPlan.getIntent(),
                    enabledTools.size(),
                    queryPlan == null ? "queryPlan 为空" : "无需工具或无可用工具");
            return emptyPlan("当前问题无需工具调用");
        }

        String question = safeQuestion(queryPlan);
        List<ToolDescriptor> candidates = pickCandidates(question, enabledTools, webSearchEnabled);
        if (candidates.isEmpty()) {
            log.info("工具规划无候选工具: question={}, webSearchEnabled={}", question, webSearchEnabled);
            return emptyPlan("当前问题优先直接走知识库问答");
        }

        List<ToolDescriptor> selected = refineSelectionWithLlm(queryPlan, recentMessages, candidates);
        ToolExecutionStrategy strategy = decideStrategy(question, selected);
        List<ToolStep> steps = buildSteps(question, strategy, selected);
        String summary = buildSummary(question, strategy, steps, selected);
        log.info("工具规划完成: question={}, webSearchEnabled={}, candidates={}, selected={}, strategy={}, llmPlannerEnabled={}",
                question,
                webSearchEnabled,
                candidates.stream().map(ToolDescriptor::getToolId).toList(),
                selected.stream().map(ToolDescriptor::getToolId).toList(),
                strategy,
                toolProperties.isPlannerLlmEnabled());

        return ToolPlan.builder()
                .planId(UUID.randomUUID().toString())
                .strategy(strategy)
                .summary(summary)
                .llmSelected(selected.size() != candidates.size() || !sameOrder(candidates, selected))
                .candidateToolIds(candidates.stream().map(ToolDescriptor::getToolId).toList())
                .steps(steps)
                .build();
    }

    private ToolPlan emptyPlan(String summary) {
        return ToolPlan.builder()
                .planId(UUID.randomUUID().toString())
                .strategy(ToolExecutionStrategy.RAG_ONLY)
                .summary(summary)
                .llmSelected(false)
                .candidateToolIds(List.of())
                .steps(List.of())
                .build();
    }

    private String safeQuestion(QueryPlan queryPlan) {
        if (queryPlan.getRewrittenQuery() != null && !queryPlan.getRewrittenQuery().isBlank()) {
            return queryPlan.getRewrittenQuery();
        }
        return queryPlan.getRawQuestion() == null ? "" : queryPlan.getRawQuestion();
    }

    private List<ToolDescriptor> pickCandidates(String question, List<ToolDescriptor> descriptors, boolean webSearchEnabled) {
        String normalized = question.toLowerCase(Locale.ROOT);
        boolean wantsDiagram = wantsDiagram(normalized);
        boolean explicitWebSearch = wantsWebSearch(normalized);
        boolean hasExternalSignal = hasExternalSignal(normalized);
        log.debug("工具候选打分输入: question={}, webSearchEnabled={}, wantsDiagram={}, explicitWebSearch={}, hasExternalSignal={}, enabledToolIds={}",
                question,
                webSearchEnabled,
                wantsDiagram,
                explicitWebSearch,
                hasExternalSignal,
                descriptors.stream().map(ToolDescriptor::getToolId).toList());

        List<ToolDescriptor> ranked = descriptors.stream()
                .filter(descriptor -> webSearchEnabled || descriptor.getType() != ToolType.WEB_SEARCH)
                .map(descriptor -> Map.entry(descriptor, scoreDescriptor(descriptor, normalized, wantsDiagram, explicitWebSearch, hasExternalSignal)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<ToolDescriptor, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().getPriority()))
                .map(Map.Entry::getKey)
                .limit(Math.max(toolProperties.getMaxSteps(), 1))
                .collect(Collectors.toCollection(ArrayList::new));

        if (webSearchEnabled && ranked.stream().noneMatch(item -> item.getType() == ToolType.WEB_SEARCH)) {
            toolRegistry.listEnabledDescriptors().stream()
                    .filter(item -> item.getType() == ToolType.WEB_SEARCH)
                    .findFirst()
                    .ifPresent(item -> {
                        log.info("工具规划补入联网搜索候选: toolId={}", item.getToolId());
                        ranked.add(0, item);
                    });
        }

        if (wantsDiagram && ranked.stream().noneMatch(item -> item.getType() == ToolType.MERMAID_DIAGRAM)) {
            toolRegistry.listEnabledDescriptors().stream()
                    .filter(item -> item.getType() == ToolType.MERMAID_DIAGRAM)
                    .findFirst()
                    .ifPresent(item -> {
                        log.info("工具规划补入 Mermaid 候选: toolId={}", item.getToolId());
                        ranked.add(item);
                    });
        }
        return ranked.stream().limit(Math.max(toolProperties.getMaxSteps(), 1)).toList();
    }

    private int scoreDescriptor(ToolDescriptor descriptor,
                                String normalizedQuestion,
                                boolean wantsDiagram,
                                boolean explicitWebSearch,
                                boolean hasExternalSignal) {
        int score = 0;
        if (!descriptor.isEnabled() || !descriptor.isReadOnly()) {
            return 0;
        }

        if (descriptor.getType() == ToolType.MERMAID_DIAGRAM && wantsDiagram) {
            return 1000;
        }
        if (descriptor.getType() == ToolType.WEB_SEARCH && explicitWebSearch) {
            return 950;
        }
        if (descriptor.getType() == ToolType.WEB_SEARCH && hasExternalSignal) {
            score += 220;
        }

        if (descriptor.getKeywords() != null) {
            for (String keyword : descriptor.getKeywords()) {
                if (keyword != null && !keyword.isBlank() && normalizedQuestion.contains(keyword.toLowerCase(Locale.ROOT))) {
                    score += 180;
                }
            }
        }

        if (descriptor.getToolId() != null && normalizedQuestion.contains(descriptor.getToolId().toLowerCase(Locale.ROOT))) {
            score += 120;
        }
        if (descriptor.getDisplayName() != null && normalizedQuestion.contains(descriptor.getDisplayName().toLowerCase(Locale.ROOT))) {
            score += 100;
        }
        if (descriptor.getType() == ToolType.MCP_READ && score > 0) {
            score += 80;
        }
        return score;
    }

    private List<ToolDescriptor> refineSelectionWithLlm(QueryPlan queryPlan,
                                                        List<QAMessage> recentMessages,
                                                        List<ToolDescriptor> candidates) {
        if (!toolProperties.isPlannerLlmEnabled() || !llmService.isAvailable() || candidates.size() <= 1) {
            return candidates;
        }

        try {
            String suggestion = llmService.generateToolPlanningSuggestion(
                    safeQuestion(queryPlan),
                    queryPlan.getHistorySummary(),
                    candidates
            );
            if (suggestion == null || suggestion.isBlank()) {
                return candidates;
            }
            JsonNode root = objectMapper.readTree(stripCodeFence(suggestion));
            JsonNode ids = root.path("orderedToolIds");
            if (!ids.isArray() || ids.isEmpty()) {
                return candidates;
            }

            List<String> orderedIds = new ArrayList<>();
            ids.forEach(node -> orderedIds.add(node.asText()));
            Map<String, ToolDescriptor> byId = candidates.stream()
                    .collect(Collectors.toMap(ToolDescriptor::getToolId, item -> item));

            LinkedHashSet<String> unique = new LinkedHashSet<>(orderedIds);
            List<ToolDescriptor> ordered = unique.stream()
                    .map(byId::get)
                    .filter(item -> item != null)
                    .limit(Math.max(toolProperties.getMaxSteps(), 1))
                    .toList();

            return ordered.isEmpty() ? candidates : ordered;
        } catch (Exception e) {
            log.debug("LLM 工具规划解析失败，回退规则规划: {}", e.getMessage());
            return candidates;
        }
    }

    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                return trimmed.substring(firstLine + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private ToolExecutionStrategy decideStrategy(String question, List<ToolDescriptor> selected) {
        boolean hasPrimaryTool = selected.stream().anyMatch(item -> item.getType() != ToolType.MERMAID_DIAGRAM);
        boolean hasOnlyWebSearch = selected.stream()
                .filter(item -> item.getType() != ToolType.MERMAID_DIAGRAM)
                .allMatch(item -> item.getType() == ToolType.WEB_SEARCH);

        if (!hasPrimaryTool) {
            return ToolExecutionStrategy.RAG_ONLY;
        }
        if (hasOnlyWebSearch) {
            return ToolExecutionStrategy.RAG_THEN_TOOL_FALLBACK;
        }
        return ToolExecutionStrategy.TOOL_THEN_RAG;
    }

    private List<ToolStep> buildSteps(String question,
                                      ToolExecutionStrategy strategy,
                                      List<ToolDescriptor> selected) {
        List<ToolStep> steps = new ArrayList<>();
        int index = 1;
        for (ToolDescriptor descriptor : selected) {
            ToolExecutionPhase phase = descriptor.getType() == ToolType.MERMAID_DIAGRAM || descriptor.getType() == ToolType.WEB_SEARCH
                    ? ToolExecutionPhase.POST_RETRIEVAL
                    : (strategy == ToolExecutionStrategy.RAG_THEN_TOOL_FALLBACK ? ToolExecutionPhase.POST_RETRIEVAL : ToolExecutionPhase.PRE_RETRIEVAL);
            steps.add(ToolStep.builder()
                    .stepId("tool-step-" + index)
                    .toolId(descriptor.getToolId())
                    .toolType(descriptor.getType())
                    .executionPhase(phase)
                    .goal(buildGoal(question, descriptor))
                    .arguments(Map.of(
                            "question", question,
                            "displayName", descriptor.getDisplayName()
                    ))
                    .stopIfSatisfied(descriptor.getType() != ToolType.MERMAID_DIAGRAM)
                    .fallbackOnly(descriptor.getType() == ToolType.WEB_SEARCH
                            || (strategy == ToolExecutionStrategy.RAG_THEN_TOOL_FALLBACK && descriptor.getType() != ToolType.MERMAID_DIAGRAM))
                    .build());
            index++;
        }
        return steps;
    }

    private String buildGoal(String question, ToolDescriptor descriptor) {
        return switch (descriptor.getType()) {
            case MCP_READ -> "使用 %s 查询与问题相关的企业上下文".formatted(descriptor.getDisplayName());
            case WEB_SEARCH -> "联网补充与“%s”相关的公开信息".formatted(question);
            case MERMAID_DIAGRAM -> "将当前证据整理为 Mermaid 图表";
        };
    }

    private String buildSummary(String question,
                                ToolExecutionStrategy strategy,
                                List<ToolStep> steps,
                                List<ToolDescriptor> selected) {
        if (steps.isEmpty()) {
            return "当前问题将直接走知识库问答。";
        }
        String toolNames = selected.stream().map(ToolDescriptor::getDisplayName).collect(Collectors.joining(" -> "));
        return switch (strategy) {
            case TOOL_ONLY -> "问题“%s”将优先使用工具回答：%s。".formatted(question, toolNames);
            case TOOL_THEN_RAG -> "问题“%s”将先调用工具，再结合知识库整合回答：%s。".formatted(question, toolNames);
            case RAG_THEN_TOOL_FALLBACK -> "问题“%s”会先走知识库检索，如证据不足再调用工具：%s。".formatted(question, toolNames);
            case RAG_ONLY -> "当前问题将直接走知识库问答。";
        };
    }

    private boolean sameOrder(List<ToolDescriptor> left, List<ToolDescriptor> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).getToolId().equals(right.get(i).getToolId())) {
                return false;
            }
        }
        return true;
    }

    private boolean wantsDiagram(String question) {
        return question.contains("流程图") || question.contains("时序图") || question.contains("mermaid");
    }

    private boolean wantsWebSearch(String question) {
        return question.contains("联网搜索") || question.contains("网上查") || question.contains("web search") || question.contains("搜索最新");
    }

    private boolean hasExternalSignal(String question) {
        return question.contains("最新") || question.contains("官网") || question.contains("开源") || question.contains("版本")
                || question.contains("发布说明") || question.contains("互联网");
    }
}
