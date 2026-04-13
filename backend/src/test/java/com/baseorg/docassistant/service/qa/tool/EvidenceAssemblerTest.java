package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceAssemblerTest {

    private final EvidenceAssembler evidenceAssembler = new EvidenceAssembler();

    @Test
    void shouldSortExternalEvidenceByQualityScoreAndBuildStructuredSections() {
        ToolEvidence lowQuality = ToolEvidence.builder()
                .toolName("联网搜索")
                .sourceType("WEB_SEARCH")
                .title("项目 B")
                .content("<search_result>项目 B</search_result>")
                .metadata(Map.of(
                        "summaryMode", "RANKED_LIST",
                        "candidateRank", 2,
                        "contentQualityScore", 0.66,
                        "sourceCategory", "REFERENCE",
                        "domain", "github.com",
                        "url", "https://github.com/b",
                        "snippet", "项目 B 摘要"
                ))
                .build();
        ToolEvidence highQuality = ToolEvidence.builder()
                .toolName("联网搜索")
                .sourceType("WEB_SEARCH")
                .title("项目 A")
                .content("<search_result>项目 A</search_result>")
                .metadata(Map.of(
                        "summaryMode", "RANKED_LIST",
                        "candidateRank", 1,
                        "contentQualityScore", 0.91,
                        "sourceCategory", "REPOSITORY",
                        "domain", "github.com",
                        "url", "https://github.com/a",
                        "snippet", "项目 A 摘要"
                ))
                .build();

        String context = evidenceAssembler.assembleToolContext(List.of(lowQuality, highQuality));

        assertThat(context).contains("外部搜索证据摘要");
        assertThat(context).contains("外部搜索原文摘录");
        assertThat(context.indexOf("项目 A")).isLessThan(context.indexOf("项目 B"));
        assertThat(context).contains("mode=\"RANKED_LIST\"");
    }
}
