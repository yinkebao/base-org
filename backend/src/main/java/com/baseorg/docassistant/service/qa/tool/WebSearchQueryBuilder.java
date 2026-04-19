package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaWebSearchProperties;
import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.tool.WebSearchQueryPlan;
import com.baseorg.docassistant.dto.qa.tool.WebSearchSummaryMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 联网搜索查询构造器。
 * <p>
 * 该类会把自然语言问题整理成更适合公网搜索的查询计划，
 * 同时识别本轮回答更适合输出“结构化榜单”还是“自然段长文总结”。
 */
@Slf4j
@Service
public class WebSearchQueryBuilder {

    private static final Pattern RANKED_PATTERN = Pattern.compile(
            "(?i)(top\\s*\\d+|前\\s*[一二三四五六七八九十两\\d]+|列出\\s*[一二三四五六七八九十两\\d]+|\\d+\\s*个|\\d+\\s*条|\\d+\\s*项)"
    );

    private final AppQaWebSearchProperties properties;

    public WebSearchQueryBuilder(AppQaWebSearchProperties properties) {
        this.properties = properties;
    }

    public WebSearchQueryPlan buildPlan(String question, QueryPlan queryPlan) {
        String original = queryPlan != null && queryPlan.getRewrittenQuery() != null
                ? queryPlan.getRewrittenQuery()
                : question;
        String normalized = sanitizeBaseQuery(original);
        List<String> removedPresentationPhrases = collectMatchedPhrases(normalized, properties.getPresentationNoisePhrases());
        String primaryQuery = buildPrimaryQuery(normalized);
        WebSearchSummaryMode summaryMode = detectSummaryMode(original, primaryQuery);
        List<String> queries = buildVariants(normalized, primaryQuery);
        log.info("联网搜索查询规划完成: originalQuestion={}, normalizedQuery={}, primaryQuery={}, summaryMode={}, removedPresentationPhrases={}, queryVariants={}",
                original,
                normalized,
                primaryQuery,
                summaryMode,
                removedPresentationPhrases,
                queries);
        return WebSearchQueryPlan.builder()
                .originalQuestion(original)
                .normalizedQuery(normalized)
                .primaryQuery(primaryQuery)
                .queries(queries)
                .summaryMode(summaryMode)
                .build();
    }

    public List<String> buildQueries(String question, QueryPlan queryPlan) {
        return buildPlan(question, queryPlan).getQueries();
    }

    private List<String> buildVariants(String normalized, String primaryQuery) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (isViableQuery(normalized)) {
            queries.add(normalized);
        }
        if (isViableQuery(primaryQuery)) {
            queries.add(primaryQuery);
        }

        String focusedQuery = buildFocusedQuery(primaryQuery);
        if (isViableQuery(focusedQuery)) {
            queries.add(focusedQuery);
        }

        String siteConstrainedQuery = buildSiteConstrainedQuery(primaryQuery);
        if (isViableQuery(siteConstrainedQuery)) {
            queries.add(siteConstrainedQuery);
        }

        for (String part : splitQuery(primaryQuery)) {
            if (isViableQuery(part)) {
                queries.add(part);
            }
            if (queries.size() >= properties.getMaxQueryVariants()) {
                break;
            }
        }
        return new ArrayList<>(queries).stream()
                .limit(properties.getMaxQueryVariants())
                .toList();
    }

    private String sanitizeBaseQuery(String question) {
        if (question == null) {
            return "";
        }
        String normalized = question;
        for (String noise : properties.getQueryNoisePhrases()) {
            if (noise == null || noise.isBlank()) {
                continue;
            }
            normalized = normalized.replace(noise, "");
            normalized = normalized.replace(noise.toLowerCase(Locale.ROOT), "");
        }
        normalized = normalized
                .replaceAll("[?？!！]+$", "")
                .replaceAll("\\s+", " ");

        normalized = normalized.replaceAll("(^|\\s)(它|这个|那个|上面的|上述)(\\s|$)", " ")
                .replaceAll("\\s+", " ");
        return cleanupQuery(extractCurrentQuestion(normalized));
    }

    private String extractCurrentQuestion(String value) {
        if (value == null) {
            return "";
        }
        int index = value.lastIndexOf("当前问题是：");
        if (index >= 0) {
            return value.substring(index + "当前问题是：".length()).trim();
        }
        return value;
    }

    private String buildPrimaryQuery(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return "";
        }
        String primary = normalized;
        for (String phrase : properties.getPresentationNoisePhrases()) {
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            primary = primary.replace(phrase, " ");
        }
        primary = primary
                .replaceAll("(请|麻烦)?(用|按|以).{0,6}(排版|结构|方式).{0,8}", " ")
                .replaceAll("(要求|希望|请)(输出|整理|排版|总结|介绍).{0,8}(清晰|简洁|简明|详细)", " ")
                .replaceAll("(要求|希望|请)(排版|结构|表达).{0,8}", " ");
        primary = cleanupQuery(primary);
        return primary.isBlank() ? normalized : primary;
    }

    private String buildFocusedQuery(String primaryQuery) {
        if (primaryQuery == null || primaryQuery.isBlank()) {
            return "";
        }
        String focused = primaryQuery
                .replaceAll("^(帮我|请|麻烦|给我)\\s*", "")
                .replaceAll("^(查询|搜索|查一下|看一下|介绍一下|总结一下|盘点一下)\\s*", "");
        return cleanupQuery(focused);
    }

    private String buildSiteConstrainedQuery(String primaryQuery) {
        if (primaryQuery == null || primaryQuery.isBlank() || properties.getAllowedDomains().isEmpty()) {
            return "";
        }
        String lower = primaryQuery.toLowerCase(Locale.ROOT);
        for (String allowedDomain : properties.getAllowedDomains()) {
            String root = allowedDomain == null ? "" : allowedDomain.toLowerCase(Locale.ROOT).split("\\.")[0];
            if (!root.isBlank() && lower.contains(root)) {
                return cleanupQuery("site:%s %s".formatted(allowedDomain, primaryQuery));
            }
        }
        return "";
    }

    private WebSearchSummaryMode detectSummaryMode(String originalQuestion, String primaryQuery) {
        String normalized = ((originalQuestion == null ? "" : originalQuestion) + " " + (primaryQuery == null ? "" : primaryQuery))
                .toLowerCase(Locale.ROOT);
        boolean rankedSignal = properties.getRankedSummarySignals().stream()
                .filter(signal -> signal != null && !signal.isBlank())
                .anyMatch(normalized::contains);
        if (rankedSignal || RANKED_PATTERN.matcher(normalized).find()) {
            return WebSearchSummaryMode.RANKED_LIST;
        }
        boolean narrativeSignal = properties.getNarrativeSummarySignals().stream()
                .filter(signal -> signal != null && !signal.isBlank())
                .anyMatch(normalized::contains);
        return narrativeSignal ? WebSearchSummaryMode.NARRATIVE_SUMMARY : WebSearchSummaryMode.NARRATIVE_SUMMARY;
    }

    private List<String> splitQuery(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split("以及|并且|同时|,|，|；|;|\\s+和\\s+|\\s+or\\s+", properties.getMaxQueryVariants());
        List<String> queries = new ArrayList<>();
        for (String part : parts) {
            String trimmed = cleanupQuery(part);
            if (trimmed.length() >= 4
                    && !trimmed.toLowerCase(Locale.ROOT).equals(value.toLowerCase(Locale.ROOT))
                    && !isPresentationOnly(trimmed)
                    && isViableQuery(trimmed)) {
                queries.add(trimmed);
            }
        }
        return queries;
    }

    /**
     * 搜索 query 一旦在清洗后只剩下标点、助词或残缺碎片，结果质量会明显下降。
     * 这里统一做分隔符归一化和尾部碎片剔除，避免出现“， ，”这类退化查询。
     */
    private String cleanupQuery(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
                .replaceAll("[：:]+", " ")
                .replaceAll("[（(]\\s*[）)]", " ")
                .replaceAll("[，,；;、]\\s*[，,；;、]+", "，")
                .replaceAll("\\s*[，,；;、]\\s*", "，")
                .replaceAll("\\s+", " ")
                .trim();

        cleaned = cleaned
                .replaceAll("^[，,；;、\\-\\s]+", "")
                .replaceAll("[，,；;、\\-\\s]+$", "")
                .replaceAll("(，的)+$", "")
                .replaceAll("(^|，)(的|了|吗|呀|呢)(?=，|$)", "")
                .replaceAll("[，,；;、]\\s*(的|了|吗|呀|呢)(?=，|$)", "");

        cleaned = cleaned
                .replaceAll("[，,；;、]{2,}", "，")
                .replaceAll("\\s+", " ")
                .trim()
                .replaceAll("^[，,；;、]+|[，,；;、]+$", "");
        return cleaned;
    }

    private boolean isViableQuery(String value) {
        if (value == null) {
            return false;
        }
        String cleaned = cleanupQuery(value);
        if (cleaned.isBlank() || cleaned.length() < 3) {
            return false;
        }
        String compact = cleaned.replaceAll("[，,；;、\\s]+", "");
        if (compact.isBlank()) {
            return false;
        }
        long meaningfulChars = compact.codePoints()
                .filter(codePoint -> Character.isLetterOrDigit(codePoint) || isChinese(codePoint))
                .count();
        return meaningfulChars >= 3;
    }

    private boolean isChinese(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN;
    }

    private List<String> collectMatchedPhrases(String text, List<String> phrases) {
        if (text == null || text.isBlank() || phrases == null || phrases.isEmpty()) {
            return List.of();
        }
        return phrases.stream()
                .filter(phrase -> phrase != null && !phrase.isBlank())
                .filter(text::contains)
                .distinct()
                .toList();
    }

    private boolean isPresentationOnly(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return properties.getPresentationNoisePhrases().stream()
                .filter(phrase -> phrase != null && !phrase.isBlank())
                .anyMatch(phrase -> normalized.contains(phrase.toLowerCase(Locale.ROOT)));
    }
}
