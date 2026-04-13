package com.baseorg.docassistant.dto.qa.tool;

/**
 * 联网搜索汇总问题的回答模式。
 */
public enum WebSearchSummaryMode {
    /**
     * 明确要求排名、前几项、若干条时，输出结构化榜单。
     */
    RANKED_LIST,

    /**
     * 泛汇总、综述、分析类问题，输出自然段长文。
     */
    NARRATIVE_SUMMARY
}
