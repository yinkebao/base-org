package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaWebSearchProperties;
import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.tool.FetchedWebPage;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionRequest;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionResult;
import com.baseorg.docassistant.dto.qa.tool.ToolType;
import com.baseorg.docassistant.dto.qa.tool.PromptRiskLevel;
import com.baseorg.docassistant.dto.qa.tool.WebSearchCandidate;
import com.baseorg.docassistant.dto.qa.tool.WebSearchQueryPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 联网搜索工具执行器。
 * <p>
 * 当前先提供统一结果结构与清晰的占位语义，后续替换为真实搜索连接器后
 * 无需改动 QAService 主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchToolHandler implements QAToolHandler {

    private final AppQaWebSearchProperties properties;
    private final WebSearchQueryBuilder queryBuilder;
    private final WebSearchGateway webSearchGateway;
    private final WebContentFetchGateway webContentFetchGateway;
    private final WebSearchContentSanitizer webSearchContentSanitizer;

    @Override
    public ToolType getSupportedType() {
        return ToolType.WEB_SEARCH;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ToolDescriptor descriptor = request.getDescriptor();
        QueryPlan queryPlan = request.getQueryPlan();
        WebSearchQueryPlan searchPlan = queryBuilder.buildPlan(request.getQuestion(), queryPlan);
        List<String> queries = searchPlan.getQueries();
        log.info("联网搜索开始: toolId={}, summaryMode={}, normalizedQuery={}, primaryQuery={}, queryVariants={}, question={}",
                descriptor.getToolId(),
                searchPlan.getSummaryMode(),
                searchPlan.getNormalizedQuery(),
                searchPlan.getPrimaryQuery(),
                queries,
                request.getQuestion());
        List<WebSearchCandidate> candidates = webSearchGateway.search(descriptor, queries, properties.getMaxSearchResults());
        List<WebSearchCandidate> filteredCandidates = webSearchContentSanitizer.filterCandidates(candidates, searchPlan.getSummaryMode());
        log.info("联网搜索候选完成: toolId={}, candidateCount={}, filteredCandidateCount={}",
                descriptor.getToolId(),
                candidates.size(),
                filteredCandidates.size());

        List<ToolEvidence> evidences = new java.util.ArrayList<>();
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        int totalLength = 0;
        for (WebSearchCandidate candidate : filteredCandidates) {
            if (evidences.size() >= properties.getMaxFetchPages()) {
                break;
            }
            java.util.Optional<FetchedWebPage> page = webContentFetchGateway.fetch(candidate)
                    .flatMap(fetched -> webSearchContentSanitizer.sanitizePage(candidate, fetched));
            if (page.isEmpty()) {
                continue;
            }
            FetchedWebPage fetched = page.get();
            String content = buildEvidenceContent(candidate, fetched);
            totalLength += content.length();
            if (totalLength > properties.getMaxTotalEvidenceLength()) {
                break;
            }
            domains.add(candidate.getDomain());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("url", candidate.getUrl());
            metadata.put("domain", candidate.getDomain());
            metadata.put("publishedAt", candidate.getPublishedAt());
            metadata.put("score", candidate.getScore());
            metadata.put("external", true);
            metadata.put("sourceLabel", "外部来源");
            metadata.put("externalProvider", descriptor.getServerName());
            metadata.put("mcpServer", descriptor.getServerName());
            metadata.put("queryVariants", queries);
            metadata.put("candidateCount", candidates.size());
            metadata.put("acceptedDomains", List.copyOf(domains));
            metadata.put("normalizedQuery", searchPlan.getNormalizedQuery());
            metadata.put("primaryQuery", searchPlan.getPrimaryQuery());
            metadata.put("summaryMode", searchPlan.getSummaryMode().name());
            metadata.put("candidateRank", candidate.getCandidateRank());
            metadata.put("contentQualityScore", candidate.getQualityScore());
            metadata.put("sourceCategory", candidate.getSourceCategory());
            metadata.put("riskLevel", fetched.isSuspicious() ? PromptRiskLevel.MEDIUM_RISK.name() : PromptRiskLevel.LOW_RISK.name());
            metadata.put("riskReason", fetched.getSuspiciousReason());
            metadata.put("snippet", clip(safe(candidate.getSnippet()), properties.getMaxSnippetLength()));
            evidences.add(ToolEvidence.builder()
                    .evidenceId(UUID.randomUUID().toString())
                    .toolId(descriptor.getToolId())
                    .toolName(descriptor.getDisplayName())
                    .sourceType(descriptor.getType().name())
                    .title(candidate.getTitle())
                    .content(content)
                    .metadata(metadata)
                    .build());
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("queries", queries);
        output.put("candidateCount", candidates.size());
        output.put("filteredCandidateCount", filteredCandidates.size());
        output.put("evidenceCount", evidences.size());
        output.put("domains", domains);
        output.put("externalProvider", descriptor.getServerName());
        output.put("mcpServer", descriptor.getServerName());
        output.put("apiKeyConfigured", true);
        output.put("summaryMode", searchPlan.getSummaryMode().name());

        if (evidences.isEmpty()) {
            log.warn("联网搜索未获得可靠证据: toolId={}, queryVariants={}, candidateCount={}, filteredCandidateCount={}",
                    descriptor.getToolId(),
                    queries,
                    candidates.size(),
                    filteredCandidates.size());
            return ToolExecutionResult.builder()
                    .stepId(request.getStep().getStepId())
                    .toolId(descriptor.getToolId())
                    .success(false)
                    .summary("联网搜索未获取到可靠的外部结果")
                    .errorMessage("外部搜索未命中白名单可信站点，或抓取内容被安全策略过滤")
                    .evidences(List.of())
                    .diagrams(List.of())
                    .output(output)
                    .build();
        }

        log.info("联网搜索完成: toolId={}, evidenceCount={}, acceptedDomains={}",
                descriptor.getToolId(),
                evidences.size(),
                domains);
        return ToolExecutionResult.builder()
                .stepId(request.getStep().getStepId())
                .toolId(descriptor.getToolId())
                .success(true)
                .summary("已补充 %s 条外部公开来源，将按 %s 模式组织回答".formatted(
                        evidences.size(),
                        searchPlan.getSummaryMode().name().equals("RANKED_LIST") ? "结构化榜单" : "自然段总结"
                ))
                .evidences(evidences)
                .diagrams(List.of())
                .output(output)
                .build();
    }

    private String buildEvidenceContent(WebSearchCandidate candidate, FetchedWebPage page) {
        return """
                <search_result rank="%s" quality_score="%.3f" source_category="%s">
                <search_risk level="%s" reason="%s" />
                <search_source title="%s" url="%s" domain="%s" published_at="%s" />
                摘要：%s

                正文摘录：
                %s
                </search_result>
                """.formatted(
                candidate.getCandidateRank(),
                candidate.getQualityScore(),
                safe(candidate.getSourceCategory()),
                page.isSuspicious() ? PromptRiskLevel.MEDIUM_RISK.name() : PromptRiskLevel.LOW_RISK.name(),
                safe(page.getSuspiciousReason()),
                safe(candidate.getTitle()),
                safe(candidate.getUrl()),
                safe(candidate.getDomain()),
                safe(candidate.getPublishedAt()),
                clip(safe(candidate.getSnippet()), properties.getMaxSnippetLength()),
                clip(safe(page.getContent()), properties.getMaxPageContentLength())
        );
    }

    private String clip(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }
}
