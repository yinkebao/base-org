package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaWebSearchProperties;
import com.baseorg.docassistant.dto.qa.tool.FetchedWebPage;
import com.baseorg.docassistant.dto.qa.tool.PromptRiskLevel;
import com.baseorg.docassistant.dto.qa.tool.WebSearchCandidate;
import com.baseorg.docassistant.dto.qa.tool.WebSearchSummaryMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 联网搜索内容过滤与清洗。
 * <p>
     * 该类会先对搜索候选做质量评分，再对抓取到的正文做安全与信息密度清洗。
     */
@Slf4j
@Service
public class WebSearchContentSanitizer {

    private record PromptRiskAssessment(PromptRiskLevel level, String pattern, String reason) {
    }

    private final AppQaWebSearchProperties properties;

    public WebSearchContentSanitizer(AppQaWebSearchProperties properties) {
        this.properties = properties;
    }

    public List<WebSearchCandidate> filterCandidates(List<WebSearchCandidate> candidates, WebSearchSummaryMode summaryMode) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<WebSearchCandidate> accepted = new ArrayList<>();
        for (WebSearchCandidate candidate : candidates) {
            if (candidate == null || candidate.getUrl() == null || candidate.getUrl().isBlank()) {
                log.debug("候选过滤丢弃: reason=缺少 URL");
                continue;
            }
            String rejectReason = rejectReason(candidate);
            if (rejectReason != null) {
                log.debug("候选过滤丢弃: title={}, domain={}, reason={}",
                        candidate.getTitle(),
                        candidate.getDomain(),
                        rejectReason);
                continue;
            }

            candidate.setSourceCategory(classifySourceCategory(candidate));
            candidate.setQualityScore(scoreCandidate(candidate, summaryMode));
            if (candidate.getQualityScore() < properties.getMinCandidateQualityScore()) {
                log.debug("候选过滤丢弃: title={}, domain={}, sourceCategory={}, reason=质量分过低, rawScore={}, qualityScore={}",
                        candidate.getTitle(),
                        candidate.getDomain(),
                        candidate.getSourceCategory(),
                        candidate.getScore(),
                        candidate.getQualityScore());
                continue;
            }
            accepted.add(candidate);
            log.debug("候选过滤保留: title={}, domain={}, sourceCategory={}, rawScore={}, qualityScore={}, summaryMode={}",
                    candidate.getTitle(),
                    candidate.getDomain(),
                    candidate.getSourceCategory(),
                    candidate.getScore(),
                    candidate.getQualityScore(),
                    summaryMode);
        }

        accepted.sort(Comparator.comparingDouble(WebSearchCandidate::getQualityScore).reversed()
                .thenComparing(Comparator.comparingDouble(WebSearchCandidate::getScore).reversed()));
        for (int i = 0; i < accepted.size(); i++) {
            accepted.get(i).setCandidateRank(i + 1);
        }
        return accepted.stream()
                .limit(properties.getMaxFetchPages())
                .toList();
    }

    public Optional<FetchedWebPage> sanitizePage(WebSearchCandidate candidate, FetchedWebPage page) {
        if (candidate == null || page == null || page.getContent() == null || page.getContent().isBlank()) {
            return Optional.empty();
        }
        String text = page.getContent()
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("(?is)<noscript.*?>.*?</noscript>", " ")
                .replaceAll("(?is)<svg.*?>.*?</svg>", " ")
                .replaceAll("(?is)<footer.*?>.*?</footer>", " ")
                .replaceAll("(?is)<nav.*?>.*?</nav>", " ")
                .replaceAll("(?is)<header.*?>.*?</header>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();

        if (text.length() < properties.getMinPageContentLength()) {
            log.debug("正文过滤丢弃: title={}, url={}, reason=页面过短, length={}",
                    candidate.getTitle(),
                    candidate.getUrl(),
                    text.length());
            return Optional.empty();
        }

        PromptRiskAssessment riskAssessment = assessPromptRisk(candidate, text);
        if (riskAssessment.level() == PromptRiskLevel.HIGH_RISK) {
            log.debug("正文过滤丢弃: title={}, url={}, reason=高风险注入, pattern={}, detail={}",
                    candidate.getTitle(),
                    candidate.getUrl(),
                    riskAssessment.pattern(),
                    riskAssessment.reason());
            return Optional.empty();
        }

        if (!hasSufficientInformationDensity(text)) {
            log.debug("正文过滤丢弃: title={}, url={}, reason=信息密度不足",
                    candidate.getTitle(),
                    candidate.getUrl());
            return Optional.empty();
        }

        if (riskAssessment.level() == PromptRiskLevel.MEDIUM_RISK) {
            log.debug("正文过滤标记: title={}, url={}, riskLevel={}, pattern={}, detail={}",
                    candidate.getTitle(),
                    candidate.getUrl(),
                    riskAssessment.level(),
                    riskAssessment.pattern(),
                    riskAssessment.reason());
        }

        return Optional.of(FetchedWebPage.builder()
                .url(candidate.getUrl())
                .domain(candidate.getDomain())
                .title(candidate.getTitle())
                .publishedAt(extractPublishedAt(page.getContent(), candidate.getPublishedAt()))
                .content(clipRelevantContent(text, properties.getMaxPageContentLength(),
                        riskAssessment.level() == PromptRiskLevel.MEDIUM_RISK))
                .suspicious(riskAssessment.level() != PromptRiskLevel.LOW_RISK)
                .suspiciousReason(riskAssessment.level() == PromptRiskLevel.LOW_RISK
                        ? null
                        : "%s:%s".formatted(riskAssessment.level().name(), riskAssessment.reason()))
                .build());
    }

    private String rejectReason(WebSearchCandidate candidate) {
        String domain = candidate.getDomain();
        if (domain == null || domain.isBlank()) {
            domain = extractDomain(candidate.getUrl());
            candidate.setDomain(domain);
        }
        if (!isAllowedDomain(domain)) {
            return "非白名单域名";
        }

        String lowerTitle = lower(candidate.getTitle());
        String lowerSnippet = lower(candidate.getSnippet());
        for (String keyword : properties.getLowQualityKeywords()) {
            if (keyword != null && !keyword.isBlank()
                    && (lowerTitle.contains(keyword.toLowerCase(Locale.ROOT))
                    || lowerSnippet.contains(keyword.toLowerCase(Locale.ROOT)))) {
                return "命中低质词: " + keyword;
            }
        }
        return null;
    }

    private boolean isAllowedDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        return properties.getAllowedDomains().stream()
                .anyMatch(allowed -> domain.equalsIgnoreCase(allowed) || domain.toLowerCase(Locale.ROOT).endsWith("." + allowed.toLowerCase(Locale.ROOT)));
    }

    private String extractDomain(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractPublishedAt(String html, String fallback) {
        if (html == null || html.isBlank()) {
            return fallback;
        }
        String[] patterns = {
                "(?is)article:published_time\"\\s*content=\"([^\"]+)\"",
                "(?is)article:modified_time\"\\s*content=\"([^\"]+)\"",
                "(?is)name=\"date\"\\s*content=\"([^\"]+)\"",
                "(?is)datetime=\"([^\"]{8,40})\""
        };
        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(html);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return fallback;
    }

    private String clipRelevantContent(String text, int maxLength, boolean cautiousMode) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        int effectiveLength = cautiousMode ? Math.min(maxLength, Math.max(maxLength * 2 / 3, 900)) : maxLength;
        int headLength = Math.min(properties.getMaxExcerptHeadLength(), Math.max(effectiveLength * 2 / 3, effectiveLength / 2));
        int middleLength = Math.min(properties.getMaxExcerptMiddleLength(), Math.max(effectiveLength - headLength, effectiveLength / 3));
        int middleStart = Math.max((text.length() / 2) - (middleLength / 2), headLength);
        int middleEnd = Math.min(middleStart + middleLength, text.length());
        String head = text.substring(0, Math.min(headLength, text.length()));
        String middle = middleStart < middleEnd ? text.substring(middleStart, middleEnd) : "";
        if (middle.isBlank()) {
            return head + "...";
        }
        return head + "\n...\n" + middle + "...";
    }

    private boolean hasSufficientInformationDensity(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        String[] tokens = normalized.split("[\\s\\p{Punct}]+");
        long informativeTokens = java.util.Arrays.stream(tokens)
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .count();
        return informativeTokens >= 18;
    }

    /**
     * 候选排序不直接看 Tavily 原始分。
     * 对榜单型问题更偏向“集合页/趋势页”，对叙述型问题则允许文档和文章类证据更靠前。
     */
    private double scoreCandidate(WebSearchCandidate candidate, WebSearchSummaryMode summaryMode) {
        double score = Math.max(candidate.getScore(), 0D) * 0.55;
        score += domainTrustScore(candidate.getDomain()) * 0.15;
        score += sourceCategoryScore(classifySourceCategory(candidate), summaryMode) * 0.15;
        score += informationDensityScore(candidate) * 0.10;
        score += freshnessScore(candidate.getPublishedAt()) * 0.05;
        return Math.min(score, 1.2);
    }

    private double informationDensityScore(WebSearchCandidate candidate) {
        int titleLength = candidate.getTitle() == null ? 0 : candidate.getTitle().trim().length();
        int snippetLength = candidate.getSnippet() == null ? 0 : candidate.getSnippet().trim().length();
        if (titleLength >= 18 && snippetLength >= 80) {
            return 1.0;
        }
        if (titleLength >= 10 && snippetLength >= 40) {
            return 0.6;
        }
        return 0.2;
    }

    private double domainTrustScore(String domain) {
        if (domain == null || domain.isBlank()) {
            return 0.2;
        }
        String lower = domain.toLowerCase(Locale.ROOT);
        if (lower.equals("github.com") || lower.endsWith(".github.com")) {
            return 1.0;
        }
        if (lower.contains("docs.") || lower.contains("developer.") || lower.endsWith("openai.com") || lower.endsWith("spring.io")) {
            return 0.9;
        }
        return 0.7;
    }

    private String classifySourceCategory(WebSearchCandidate candidate) {
        String url = lower(candidate.getUrl());
        String title = lower(candidate.getTitle());
        String snippet = lower(candidate.getSnippet());
        if (url.contains("/trending")
                || url.contains("/topics/")
                || title.contains("trending")
                || title.contains("top ")
                || title.contains("热门")
                || title.contains("榜单")
                || title.contains("排行")
                || title.contains("popular")
                || snippet.contains("trending")
                || snippet.contains("popular")
                || snippet.contains("hot repositories")) {
            return "TRENDING_OR_COLLECTION";
        }
        if (url.matches(".*/[^/]+/[^/]+/?$") || url.contains("/tree/") || url.contains("/blob/")) {
            return "REPOSITORY";
        }
        if (url.contains("/docs/") || title.contains("文档") || title.contains("documentation")) {
            return "DOCUMENTATION";
        }
        if (title.contains("发布") || title.contains("更新") || title.contains("博客") || url.contains("/blog/")) {
            return "ARTICLE";
        }
        return "REFERENCE";
    }

    private double sourceCategoryScore(String sourceCategory, WebSearchSummaryMode summaryMode) {
        if (summaryMode == WebSearchSummaryMode.RANKED_LIST) {
            return switch (sourceCategory) {
                case "TRENDING_OR_COLLECTION" -> 1.0;
                case "REPOSITORY" -> 0.78;
                case "DOCUMENTATION" -> 0.7;
                case "ARTICLE" -> 0.62;
                default -> 0.55;
            };
        }
        return switch (sourceCategory) {
            case "TRENDING_OR_COLLECTION" -> 0.95;
            case "DOCUMENTATION" -> 0.92;
            case "ARTICLE" -> 0.82;
            case "REPOSITORY" -> 0.78;
            default -> 0.65;
        };
    }

    /**
     * 可信域名里出现“system prompt”之类术语时，很多只是正文讨论提示工程概念，
     * 不应直接判死刑。这里只在出现明确的行为劫持语义时才视为高风险。
     */
    private PromptRiskAssessment assessPromptRisk(WebSearchCandidate candidate, String text) {
        String lower = lower(text);
        for (String pattern : properties.getHighRiskPromptInjectionPatterns()) {
            if (lower.contains(pattern.toLowerCase(Locale.ROOT))) {
                return new PromptRiskAssessment(PromptRiskLevel.HIGH_RISK, pattern, "命中明确行为劫持语义");
            }
        }
        for (String pattern : properties.getMediumRiskPromptSignals()) {
            if (lower.contains(pattern.toLowerCase(Locale.ROOT))) {
                return new PromptRiskAssessment(
                        isTrustedDomain(candidate.getDomain()) ? PromptRiskLevel.LOW_RISK : PromptRiskLevel.MEDIUM_RISK,
                        pattern,
                        isTrustedDomain(candidate.getDomain()) ? "可信域名中的提示工程术语，降级保留" : "命中提示工程相关词"
                );
            }
        }
        return new PromptRiskAssessment(PromptRiskLevel.LOW_RISK, "", "未发现明显注入特征");
    }

    private boolean isTrustedDomain(String domain) {
        return domainTrustScore(domain) >= 0.9;
    }

    private double freshnessScore(String publishedAt) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return 0.4;
        }
        try {
            OffsetDateTime published = OffsetDateTime.parse(publishedAt);
            long days = Math.max(Duration.between(published, OffsetDateTime.now()).toDays(), 0);
            if (days <= 14) {
                return 1.0;
            }
            if (days <= 90) {
                return 0.7;
            }
            return 0.4;
        } catch (DateTimeParseException e) {
            return 0.5;
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
