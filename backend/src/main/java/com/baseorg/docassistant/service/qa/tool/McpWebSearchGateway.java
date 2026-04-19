package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaWebSearchProperties;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.WebSearchCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 基于 MCP 的联网搜索网关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpWebSearchGateway implements WebSearchGateway {

    private final McpGateway mcpGateway;
    private final AppQaWebSearchProperties webSearchProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<WebSearchCandidate> search(ToolDescriptor descriptor, List<String> queries, int maxResults) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }

        List<WebSearchCandidate> candidates = new ArrayList<>();
        LinkedHashSet<String> seenUrls = new LinkedHashSet<>();
        for (String query : queries) {
            int remainingBudget = maxResults - candidates.size();
            if (remainingBudget <= 0) {
                break;
            }

            Map<String, Object> payload = mcpGateway.call(descriptor, query, buildSearchArguments(query, remainingBudget));
            if (!Boolean.TRUE.equals(payload.getOrDefault("available", true))) {
                log.warn("MCP 搜索不可用: server={}, reason={}", descriptor.getServerName(), payload.get("summary"));
                continue;
            }
            if (Boolean.TRUE.equals(payload.get("isError"))) {
                log.warn("MCP 搜索返回错误: server={}, query={}, summary={}, errorMessage={}, content={}, meta={}",
                        descriptor.getServerName(),
                        query,
                        clipForLog(payload.get("summary")),
                        clipForLog(payload.get("errorMessage")),
                        clipForLog(payload.get("content")),
                        clipForLog(payload.get("meta")));
                continue;
            }

            List<Map<String, Object>> rows = extractRows(payload);
            if (rows.isEmpty()) {
                log.debug("MCP 搜索结果为空: server={}, query={}, payloadKeys={}, summary={}",
                        descriptor.getServerName(),
                        query,
                        payload.keySet(),
                        clipForLog(payload.get("summary")));
            }
            for (Map<String, Object> row : rows) {
                WebSearchCandidate candidate = mapRow(row);
                if (candidate == null || candidate.getUrl() == null || candidate.getUrl().isBlank()) {
                    continue;
                }
                if (!seenUrls.add(candidate.getUrl())) {
                    continue;
                }
                candidates.add(candidate);
                if (candidates.size() >= maxResults) {
                    break;
                }
            }
        }
        log.debug("MCP 搜索候选数量: {}", candidates.size());
        return candidates;
    }

    private Map<String, Object> buildSearchArguments(String query, int maxResults) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", query);
        arguments.put("search_depth", "basic");
        arguments.put("max_results", Math.max(maxResults, 1));
        arguments.put("include_images", false);
        arguments.put("include_favicon", true);
        arguments.put("include_raw_content", false);
        if (webSearchProperties.getAllowedDomains() != null && !webSearchProperties.getAllowedDomains().isEmpty()) {
            arguments.put("include_domains", webSearchProperties.getAllowedDomains());
        }
        return arguments;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Map<String, Object> payload) {
        Object rows = payload.get("results");
        if (rows == null) {
            rows = payload.get("items");
        }
        if (rows == null && payload.get("structuredContent") instanceof Map<?, ?> structuredContent) {
            rows = structuredContent.get("results");
            if (rows == null) {
                rows = structuredContent.get("items");
            }
        }
        if (rows == null) {
            return List.of();
        }
        return objectMapper.convertValue(rows, List.class);
    }

    private WebSearchCandidate mapRow(Map<String, Object> row) {
        try {
            String url = stringOf(row.get("url"));
            String title = stringOf(firstNonNull(row, "title", "name"));
            String snippet = stringOf(firstNonNull(row, "snippet", "summary", "content", "raw_content"));
            String publishedAt = stringOf(firstNonNull(row, "publishedAt", "published_at", "date", "updatedAt", "published_date"));
            double score = scoreOf(firstNonNull(row, "score", "rank", "relevance"));
            String domain = extractDomain(url);

            return WebSearchCandidate.builder()
                    .url(url)
                    .title(title)
                    .snippet(snippet)
                    .publishedAt(publishedAt)
                    .domain(domain)
                    .score(score)
                    .build();
        } catch (Exception e) {
            log.debug("解析 MCP 搜索结果失败: {}", e.getMessage());
            return null;
        }
    }

    private Object firstNonNull(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double scoreOf(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String extractDomain(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (Exception e) {
            return "";
        }
    }

    private String clipForLog(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace('\n', ' ').trim();
        if (text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500) + "...";
    }
}
