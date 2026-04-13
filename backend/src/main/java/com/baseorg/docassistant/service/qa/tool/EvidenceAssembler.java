package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.WebSearchSummaryMode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一组装工具证据与检索证据。
 */
@Service
public class EvidenceAssembler {

    public String assembleToolContext(List<ToolEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return "";
        }
        List<ToolEvidence> externalSearchEvidences = sortExternalSearchEvidences(evidences.stream()
                .filter(evidence -> "WEB_SEARCH".equalsIgnoreCase(evidence.getSourceType()))
                .toList());
        List<ToolEvidence> internalToolEvidences = evidences.stream()
                .filter(evidence -> !"WEB_SEARCH".equalsIgnoreCase(evidence.getSourceType()))
                .toList();

        StringBuilder sb = new StringBuilder("工具证据：\n");
        if (!internalToolEvidences.isEmpty()) {
            sb.append("内部工具证据：\n");
            for (int i = 0; i < internalToolEvidences.size(); i++) {
                ToolEvidence evidence = internalToolEvidences.get(i);
                sb.append("<tool_evidence index=\"").append(i + 1).append("\" tool=\"")
                        .append(evidence.getToolName()).append("\" title=\"")
                        .append(evidence.getTitle()).append("\">\n")
                        .append(StringUtils.hasText(evidence.getContent()) ? evidence.getContent() : "")
                        .append("\n</tool_evidence>\n");
            }
        }

        if (!externalSearchEvidences.isEmpty()) {
            sb.append("外部搜索证据摘要：\n");
            for (int i = 0; i < externalSearchEvidences.size(); i++) {
                ToolEvidence evidence = externalSearchEvidences.get(i);
                Map<String, Object> metadata = metadataMap(evidence);
                sb.append("<external_search_summary index=\"").append(i + 1)
                        .append("\" mode=\"").append(readSummaryMode(metadata).name())
                        .append("\" candidate_rank=\"").append(valueOf(metadata.get("candidateRank")))
                        .append("\" quality_score=\"").append(valueOf(metadata.get("contentQualityScore")))
                        .append("\" source_category=\"").append(valueOf(metadata.get("sourceCategory")))
                        .append("\" domain=\"").append(valueOf(metadata.get("domain")))
                        .append("\" url=\"").append(valueOf(metadata.get("url")))
                        .append("\">\n")
                        .append("标题：").append(evidence.getTitle()).append("\n")
                        .append("摘要：").append(valueOf(metadata.get("snippet"))).append("\n")
                        .append("</external_search_summary>\n");
            }
            sb.append("外部搜索原文摘录：\n");
            for (int i = 0; i < externalSearchEvidences.size(); i++) {
                ToolEvidence evidence = externalSearchEvidences.get(i);
                sb.append("<external_search_excerpt index=\"").append(i + 1).append("\" tool=\"")
                        .append(evidence.getToolName()).append("\">\n")
                        .append(StringUtils.hasText(evidence.getContent()) ? evidence.getContent() : "")
                        .append("\n</external_search_excerpt>\n");
            }
        }
        return sb.toString().trim();
    }

    public String assembleRetrievalContext(List<SearchResult.ResultItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("知识库证据：\n");
        for (int i = 0; i < Math.min(items.size(), 4); i++) {
            SearchResult.ResultItem item = items.get(i);
            sb.append(i + 1)
                    .append(". [")
                    .append(item.getDocTitle())
                    .append("] ")
                    .append(item.getContent())
                    .append("\n");
        }
        return sb.toString();
    }

    public String assembleCombinedContext(List<ToolEvidence> evidences, List<SearchResult.ResultItem> items) {
        String toolContext = assembleToolContext(evidences);
        String retrievalContext = assembleRetrievalContext(items);
        if (toolContext.isBlank()) {
            return retrievalContext;
        }
        if (retrievalContext.isBlank()) {
            return toolContext;
        }
        return toolContext + "\n" + retrievalContext;
    }

    private List<ToolEvidence> sortExternalSearchEvidences(List<ToolEvidence> evidences) {
        return evidences.stream()
                .sorted(Comparator
                        .comparingDouble((ToolEvidence evidence) -> sourceCategoryBoost(evidence)).reversed()
                        .thenComparingDouble((ToolEvidence evidence) -> metadataDouble(evidence, "contentQualityScore")).reversed()
                        .thenComparingInt(evidence -> metadataInt(evidence, "candidateRank"))
                        .thenComparing((ToolEvidence evidence) -> valueOf(metadataMap(evidence).get("publishedAt")), Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private double sourceCategoryBoost(ToolEvidence evidence) {
        String sourceCategory = valueOf(metadataMap(evidence).get("sourceCategory"));
        WebSearchSummaryMode summaryMode = readSummaryMode(metadataMap(evidence));
        if (summaryMode == WebSearchSummaryMode.RANKED_LIST) {
            return "TRENDING_OR_COLLECTION".equalsIgnoreCase(sourceCategory) ? 1.0 : 0.6;
        }
        return switch (sourceCategory) {
            case "DOCUMENTATION" -> 1.0;
            case "ARTICLE" -> 0.9;
            case "TRENDING_OR_COLLECTION" -> 0.85;
            case "REPOSITORY" -> 0.7;
            default -> 0.6;
        };
    }

    private Map<String, Object> metadataMap(ToolEvidence evidence) {
        return evidence.getMetadata() == null ? Map.of() : evidence.getMetadata();
    }

    private double metadataDouble(ToolEvidence evidence, String key) {
        Object value = metadataMap(evidence).get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0D : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    private int metadataInt(ToolEvidence evidence, String key) {
        Object value = metadataMap(evidence).get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private WebSearchSummaryMode readSummaryMode(Map<String, Object> metadata) {
        try {
            return WebSearchSummaryMode.valueOf(valueOf(metadata.get("summaryMode")));
        } catch (Exception e) {
            return WebSearchSummaryMode.NARRATIVE_SUMMARY;
        }
    }

    private String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
