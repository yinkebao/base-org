package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.QueryPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryUnderstandingServiceTest {

    private final QueryUnderstandingService service = new QueryUnderstandingService();

    @Test
    void shouldRecognizeSmallTalkGreetings() {
        QueryPlan plan = service.plan("你好！", List.of());

        assertThat(plan.getIntent()).isEqualTo(QueryPlan.Intent.SMALL_TALK);
        assertThat(plan.isShouldSkipRetrieval()).isTrue();
        assertThat(plan.getRewrittenQuery()).isEqualTo("你好");
    }

    @Test
    void shouldKeepBusinessQuestionAsRagSearch() {
        QueryPlan plan = service.plan("系统如何鉴权？", List.of());

        assertThat(plan.getIntent()).isEqualTo(QueryPlan.Intent.RAG_SEARCH);
        assertThat(plan.isShouldSkipRetrieval()).isFalse();
    }
}
