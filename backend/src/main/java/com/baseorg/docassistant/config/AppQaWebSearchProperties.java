package com.baseorg.docassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 联网搜索配置。
 */
@Data
@ConfigurationProperties(prefix = "app.qa.web-search")
public class AppQaWebSearchProperties {

    private boolean enabled = true;

    private double lowConfidenceThreshold = 0.72;

    private double topScoreThreshold = 0.82;

    private int minReliableSources = 2;

    private int maxQueryVariants = 3;

    private int maxSearchResults = 5;

    private int maxFetchPages = 3;

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 5000;

    private int maxSnippetLength = 240;

    private int maxPageContentLength = 1800;

    private int maxTotalEvidenceLength = 4800;

    private int minPageContentLength = 120;

    private int maxExcerptHeadLength = 1100;

    private int maxExcerptMiddleLength = 700;

    private double minCandidateQualityScore = 0.35;

    private List<String> allowedDomains = new ArrayList<>();

    private List<String> queryNoisePhrases = new ArrayList<>(List.of(
            "web search",
            "联网搜索",
            "请帮我",
            "帮我",
            "麻烦",
            "请问",
            "我想知道",
            "告诉我"
    ));

    private List<String> presentationNoisePhrases = new ArrayList<>(List.of(
            "要求排版清晰",
            "排版清晰",
            "要求结构清晰",
            "结构清晰",
            "要求条理清晰",
            "条理清晰",
            "要求简洁",
            "简洁",
            "简明",
            "详细",
            "通俗易懂"
    ));

    private List<String> rankedSummarySignals = new ArrayList<>(List.of(
            "top",
            "前",
            "排名",
            "排行",
            "榜单",
            "第",
            "几条",
            "几个",
            "几项",
            "列出",
            "盘点"
    ));

    private List<String> narrativeSummarySignals = new ArrayList<>(List.of(
            "总结",
            "综述",
            "介绍",
            "分析",
            "解读",
            "现状",
            "趋势",
            "说明",
            "概览"
    ));

    private List<String> lowQualityKeywords = new ArrayList<>(List.of(
            "广告",
            "推广",
            "相关阅读",
            "点击下载",
            "立即下载",
            "注册后查看",
            "登录后查看"
    ));

    /**
     * 只有出现明确行为劫持语义时，才直接把正文判定为高风险并丢弃。
     */
    private List<String> highRiskPromptInjectionPatterns = new ArrayList<>(List.of(
            "ignore previous instructions",
            "ignore all previous instructions",
            "ignore the above instructions",
            "reveal secrets",
            "send data",
            "attacker.com",
            "send user data",
            "exfiltrate"
    ));

    /**
     * 这类词更适合作为“可疑提示工程上下文”信号，而不是一票否决。
     */
    private List<String> mediumRiskPromptSignals = new ArrayList<>(List.of(
            "system prompt",
            "developer instruction",
            "assistant instruction",
            "prompt injection",
            "jailbreak",
            "ignore instructions"
    ));
}
