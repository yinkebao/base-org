package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionBatchResult;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionPhase;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionRequest;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionResult;
import com.baseorg.docassistant.dto.qa.tool.ToolPlan;
import com.baseorg.docassistant.dto.qa.tool.ToolStep;
import com.baseorg.docassistant.dto.qa.tool.ToolTraceItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具顺序执行服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionService {

    private final QAToolRegistry toolRegistry;

    public ToolExecutionBatchResult execute(ToolPlan toolPlan,
                                            ToolExecutionPhase phase,
                                            Long userId,
                                            Long sessionId,
                                            String question,
                                            QueryPlan queryPlan,
                                            List<ToolEvidence> accumulatedEvidence,
                                            List<SearchResult.ResultItem> retrievalItems,
                                            ToolExecutionObserver observer) {
        if (toolPlan == null || toolPlan.getSteps() == null || toolPlan.getSteps().isEmpty()) {
            return emptyResult();
        }

        List<ToolTraceItem> traceItems = new ArrayList<>();
        List<ToolEvidence> newEvidences = new ArrayList<>();
        List<DiagramPayload> diagrams = new ArrayList<>();
        log.info("开始执行工具阶段: phase={}, planId={}, stepCount={}, sessionId={}",
                phase,
                toolPlan.getPlanId(),
                toolPlan.getSteps().size(),
                sessionId);

        for (ToolStep step : toolPlan.getSteps()) {
            if (step.getExecutionPhase() != phase) {
                continue;
            }

            ToolDescriptor descriptor = toolRegistry.findDescriptor(step.getToolId()).orElse(null);
            if (descriptor == null) {
                traceItems.add(failedTrace(step, "工具未注册"));
                observer.onToolError(null, step, "工具未注册");
                continue;
            }

            ToolTraceItem trace = ToolTraceItem.builder()
                    .stepId(step.getStepId())
                    .toolId(descriptor.getToolId())
                    .toolName(descriptor.getDisplayName())
                    .goal(step.getGoal())
                    .status("RUNNING")
                    .startedAt(LocalDateTime.now())
                    .build();
            traceItems.add(trace);
            observer.onToolStart(descriptor, step);
            log.info("开始执行工具: phase={}, toolId={}, toolType={}, stepId={}, fallbackOnly={}",
                    phase,
                    descriptor.getToolId(),
                    descriptor.getType(),
                    step.getStepId(),
                    step.isFallbackOnly());

            try {
                QAToolHandler handler = toolRegistry.findHandler(descriptor.getType())
                        .orElseThrow(() -> new IllegalStateException("未找到工具处理器: " + descriptor.getType()));

                ToolExecutionResult result = handler.execute(ToolExecutionRequest.builder()
                        .userId(userId)
                        .sessionId(sessionId)
                        .question(question)
                        .queryPlan(queryPlan)
                        .descriptor(descriptor)
                        .step(step)
                        .accumulatedEvidence(accumulatedEvidence == null ? List.of() : accumulatedEvidence)
                        .retrievalItems(retrievalItems == null ? List.of() : retrievalItems)
                        .build());

                trace.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
                trace.setSummary(result.getSummary());
                trace.setErrorMessage(result.getErrorMessage());
                trace.setCompletedAt(LocalDateTime.now());

                if (result.getEvidences() != null) {
                    newEvidences.addAll(result.getEvidences());
                }
                if (result.getDiagrams() != null) {
                    diagrams.addAll(result.getDiagrams());
                    result.getDiagrams().forEach(diagram -> observer.onDiagram(descriptor, step, diagram));
                }

                if (result.isSuccess()) {
                    log.info("工具执行完成: phase={}, toolId={}, success=true, evidenceCount={}, diagramCount={}, summary={}",
                            phase,
                            descriptor.getToolId(),
                            result.getEvidences() == null ? 0 : result.getEvidences().size(),
                            result.getDiagrams() == null ? 0 : result.getDiagrams().size(),
                            result.getSummary());
                    observer.onToolResult(descriptor, step, result);
                } else {
                    log.warn("工具执行失败: phase={}, toolId={}, error={}",
                            phase,
                            descriptor.getToolId(),
                            result.getErrorMessage());
                    observer.onToolError(descriptor, step, result.getErrorMessage());
                }
            } catch (Exception e) {
                trace.setStatus("FAILED");
                trace.setErrorMessage(e.getMessage());
                trace.setCompletedAt(LocalDateTime.now());
                observer.onToolError(descriptor, step, e.getMessage());
                log.warn("工具执行失败: toolId={}, reason={}", descriptor.getToolId(), e.getMessage());
            }
        }

        return ToolExecutionBatchResult.builder()
                .traceItems(traceItems)
                .evidences(newEvidences)
                .diagrams(diagrams)
                .build();
    }

    private ToolExecutionBatchResult emptyResult() {
        return ToolExecutionBatchResult.builder()
                .traceItems(List.of())
                .evidences(List.of())
                .diagrams(List.of())
                .build();
    }

    private ToolTraceItem failedTrace(ToolStep step, String errorMessage) {
        return ToolTraceItem.builder()
                .stepId(step.getStepId())
                .toolId(step.getToolId())
                .goal(step.getGoal())
                .status("FAILED")
                .errorMessage(errorMessage)
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
    }
}
