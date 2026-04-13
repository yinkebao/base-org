package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionRequest;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionResult;
import com.baseorg.docassistant.dto.qa.tool.ToolType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 只读工具执行器。
 */
@Component
@RequiredArgsConstructor
public class McpReadToolHandler implements QAToolHandler {

    private final McpGateway mcpGateway;

    @Override
    public ToolType getSupportedType() {
        return ToolType.MCP_READ;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ToolDescriptor descriptor = request.getDescriptor();
        Map<String, Object> payload = mcpGateway.call(descriptor, request.getQuestion(), request.getStep().getArguments());
        String summary = String.valueOf(payload.getOrDefault("summary", descriptor.getDisplayName() + " 已完成查询"));
        boolean available = Boolean.TRUE.equals(payload.getOrDefault("available", true));

        ToolEvidence evidence = ToolEvidence.builder()
                .evidenceId(UUID.randomUUID().toString())
                .toolId(descriptor.getToolId())
                .toolName(descriptor.getDisplayName())
                .sourceType(descriptor.getType().name())
                .title(descriptor.getDisplayName() + " 查询结果")
                .content(summary)
                .metadata(payload)
                .build();

        return ToolExecutionResult.builder()
                .stepId(request.getStep().getStepId())
                .toolId(descriptor.getToolId())
                .success(available)
                .summary(summary)
                .errorMessage(available ? null : String.valueOf(payload.getOrDefault("errorMessage", summary)))
                .evidences(List.of(evidence))
                .diagrams(List.of())
                .output(payload)
                .build();
    }
}
