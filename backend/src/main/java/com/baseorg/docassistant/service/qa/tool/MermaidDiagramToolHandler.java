package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionRequest;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionResult;
import com.baseorg.docassistant.dto.qa.tool.ToolType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mermaid 图表生成工具。
 */
@Component
public class MermaidDiagramToolHandler implements QAToolHandler {

    @Override
    public ToolType getSupportedType() {
        return ToolType.MERMAID_DIAGRAM;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ToolDescriptor descriptor = request.getDescriptor();
        String diagramType = resolveDiagramType(request.getQuestion());
        String title = resolveTitle(request.getQuestion(), diagramType);
        String mermaidDsl = "sequence".equals(diagramType)
                ? buildSequenceDiagram(request.getQuestion(), request.getAccumulatedEvidence())
                : buildFlowChart(request.getQuestion(), request.getAccumulatedEvidence(), request.getRetrievalItems());

        DiagramPayload diagram = DiagramPayload.builder()
                .diagramId(UUID.randomUUID().toString())
                .diagramType(diagramType)
                .title(title)
                .mermaidDsl(mermaidDsl)
                .sourceStepIds(List.of(request.getStep().getStepId()))
                .build();

        return ToolExecutionResult.builder()
                .stepId(request.getStep().getStepId())
                .toolId(descriptor.getToolId())
                .success(true)
                .summary("已生成 Mermaid %s".formatted("sequence".equals(diagramType) ? "时序图" : "流程图"))
                .evidences(List.of())
                .diagrams(List.of(diagram))
                .output(java.util.Map.of("diagramType", diagramType))
                .build();
    }

    private String resolveDiagramType(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        if (normalized.contains("时序图") || normalized.contains("sequence")) {
            return "sequence";
        }
        return "flowchart";
    }

    private String resolveTitle(String question, String diagramType) {
        return "%s：%s".formatted(
                "sequence".equals(diagramType) ? "问答时序图" : "问答流程图",
                question == null || question.isBlank() ? "未命名问题" : question
        );
    }

    private String buildSequenceDiagram(String question, List<ToolEvidence> evidences) {
        StringBuilder sb = new StringBuilder("""
                sequenceDiagram
                    participant User as 用户
                    participant QA as QA服务
                """);
        int index = 1;
        for (ToolEvidence evidence : safeEvidence(evidences, 3)) {
            sb.append("    participant T").append(index).append(" as ")
                    .append(sanitizeLabel(evidence.getToolName()))
                    .append("\n");
            sb.append("    QA->>T").append(index).append(": ")
                    .append(sanitizeLabel(evidence.getTitle()))
                    .append("\n");
            sb.append("    T").append(index).append("-->>QA: ")
                    .append(sanitizeLabel(trim(evidence.getContent(), 28)))
                    .append("\n");
            index++;
        }
        sb.append("    User->>QA: ").append(sanitizeLabel(trim(question, 32))).append("\n");
        sb.append("    QA-->>User: 返回结构化结论\n");
        return sb.toString();
    }

    private String buildFlowChart(String question, List<ToolEvidence> evidences, List<SearchResult.ResultItem> retrievalItems) {
        StringBuilder sb = new StringBuilder("""
                flowchart TD
                    A[用户问题]
                    B[工具规划]
                """);
        char node = 'C';
        if (evidences != null) {
            for (ToolEvidence evidence : safeEvidence(evidences, 3)) {
                sb.append("    ").append(node).append("[")
                        .append(sanitizeLabel(trim(evidence.getToolName(), 24)))
                        .append("]\n");
                node++;
            }
        }
        boolean hasRetrieval = retrievalItems != null && !retrievalItems.isEmpty();
        char retrievalNode = node;
        if (hasRetrieval) {
            sb.append("    ").append(retrievalNode).append("[知识库检索]\n");
            node++;
        }
        char answerNode = node;
        sb.append("    ").append(answerNode).append("[回答生成]\n");
        sb.append("    A --> B\n");
        char current = 'B';
        for (char cursor = 'C'; cursor < answerNode; cursor++) {
            sb.append("    ").append(current).append(" --> ").append(cursor).append("\n");
            current = cursor;
        }
        sb.append("    ").append(current).append(" --> ").append(answerNode).append("\n");
        sb.append("    %% ").append(sanitizeLabel(trim(question, 40))).append("\n");
        return sb.toString();
    }

    private List<ToolEvidence> safeEvidence(List<ToolEvidence> evidences, int limit) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(evidences.stream().limit(limit).toList());
    }

    private String trim(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value == null ? "" : value;
        }
        return value.substring(0, limit) + "...";
    }

    private String sanitizeLabel(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("[", "")
                .replace("]", "")
                .replace("(", "")
                .replace(")", "")
                .replace("\"", "")
                .replace("\n", " ");
    }
}
