package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaWebSearchProperties;
import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.tool.WebSearchQueryPlan;
import com.baseorg.docassistant.dto.qa.tool.WebSearchSummaryMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchQueryBuilderTest {

    private final WebSearchQueryBuilder queryBuilder = new WebSearchQueryBuilder(new AppQaWebSearchProperties());

    @Test
    void shouldBuildRankedListPlanAndRemovePresentationNoise() {
        QueryPlan queryPlan = QueryPlan.builder()
                .rawQuestion("帮我查询 github 热门项目的前 8 个，要求排版清晰，简洁")
                .rewrittenQuery("帮我查询 github 热门项目的前 8 个，要求排版清晰，简洁")
                .build();

        WebSearchQueryPlan plan = queryBuilder.buildPlan(queryPlan.getRawQuestion(), queryPlan);

        assertThat(plan.getSummaryMode()).isEqualTo(WebSearchSummaryMode.RANKED_LIST);
        assertThat(plan.getPrimaryQuery()).doesNotContain("排版清晰").doesNotContain("简洁");
        assertThat(plan.getPrimaryQuery()).doesNotContain("， ，").doesNotEndWith("，");
        assertThat(plan.getQueries()).anyMatch(item -> item.contains("github"));
        assertThat(plan.getQueries()).noneMatch(item -> item.equals("要求排版清晰"));
        assertThat(plan.getQueries()).noneMatch(item -> item.contains("， ，"));
    }

    @Test
    void shouldUseNarrativeSummaryForGeneralSummaryQuestion() {
        QueryPlan queryPlan = QueryPlan.builder()
                .rawQuestion("请总结一下 GitHub 当前热门开源项目的整体方向")
                .rewrittenQuery("请总结一下 GitHub 当前热门开源项目的整体方向")
                .build();

        WebSearchQueryPlan plan = queryBuilder.buildPlan(queryPlan.getRawQuestion(), queryPlan);

        assertThat(plan.getSummaryMode()).isEqualTo(WebSearchSummaryMode.NARRATIVE_SUMMARY);
        assertThat(plan.getQueries()).isNotEmpty();
    }
}
