package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.QaToolExecutorConfig;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 工具并发执行服务。
 * <p>
 * 每个 phase（PRE_RETRIEVAL / POST_RETRIEVAL）内匹配的 {@link ToolStep} 会被一次性提交到
 * 专用线程池（{@link QaToolExecutorConfig#QA_TOOL_EXECUTOR_BEAN_NAME}）并发执行，
 * 再按原始 step 顺序聚合 trace / evidence / diagram 返回，保证下游
 * {@link EvidenceAssembler} 对顺序的依赖不被破坏。<br>
 * Observer 回调通过 {@link ThreadSafeToolExecutionObserver} 串行化，避免 WebSocket 推送等
 * 下游 listener 遭遇并发写。单个 step 的异常被隔离为 FAILED trace，不影响其他 step。
 */
@Slf4j
@Service
public class ToolExecutionService {

    private final QAToolRegistry toolRegistry;
    private final Executor qaToolExecutor;

    public ToolExecutionService(QAToolRegistry toolRegistry,
                                @Qualifier(QaToolExecutorConfig.QA_TOOL_EXECUTOR_BEAN_NAME) Executor qaToolExecutor) {
        this.toolRegistry = toolRegistry;
        this.qaToolExecutor = qaToolExecutor;
    }

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

        List<ToolStep> selectedSteps = toolPlan.getSteps().stream()
                .filter(step -> step.getExecutionPhase() == phase)
                .toList();
        if (selectedSteps.isEmpty()) {
            return emptyResult();
        }

        ToolExecutionObserver safeObserver = ThreadSafeToolExecutionObserver.wrap(observer);
        log.info("开始执行工具阶段: phase={}, planId={}, stepCount={}, sessionId={}",
                phase,
                toolPlan.getPlanId(),
                selectedSteps.size(),
                sessionId);

        List<CompletableFuture<StepOutcome>> futures = new ArrayList<>(selectedSteps.size());
        for (ToolStep step : selectedSteps) {
            CompletableFuture<StepOutcome> future = CompletableFuture.supplyAsync(
                    () -> runStep(step, phase, userId, sessionId, question, queryPlan,
                            accumulatedEvidence, retrievalItems, safeObserver),
                    qaToolExecutor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            // 单个 step 的异常已在 runStep 内转为 FAILED trace，此处仅防御线程池级异常。
            log.warn("工具阶段并发执行出现外层异常: phase={}, reason={}", phase, e.getMessage(), e);
        }

        List<ToolTraceItem> traceItems = new ArrayList<>(selectedSteps.size());
        List<ToolEvidence> newEvidences = new ArrayList<>();
        List<DiagramPayload> diagrams = new ArrayList<>();
        for (CompletableFuture<StepOutcome> future : futures) {
            StepOutcome outcome;
            try {
                outcome = future.join();
            } catch (Exception e) {
                log.warn("读取工具执行结果失败: phase={}, reason={}", phase, e.getMessage());
                continue;
            }
            traceItems.add(outcome.trace());
            newEvidences.addAll(outcome.evidences());
            diagrams.addAll(outcome.diagrams());
        }

        return ToolExecutionBatchResult.builder()
                .traceItems(traceItems)
                .evidences(newEvidences)
                .diagrams(diagrams)
                .build();
    }

    private StepOutcome runStep(ToolStep step,
                                ToolExecutionPhase phase,
                                Long userId,
                                Long sessionId,
                                String question,
                                QueryPlan queryPlan,
                                List<ToolEvidence> accumulatedEvidence,
                                List<SearchResult.ResultItem> retrievalItems,
                                ToolExecutionObserver observer) {
        ToolDescriptor descriptor = toolRegistry.findDescriptor(step.getToolId()).orElse(null);
        if (descriptor == null) {
            observer.onToolError(null, step, "工具未注册");
            return new StepOutcome(failedTrace(step, "工具未注册"), List.of(), List.of());
        }

        ToolTraceItem trace = ToolTraceItem.builder()
                .stepId(step.getStepId())
                .toolId(descriptor.getToolId())
                .toolName(descriptor.getDisplayName())
                .goal(step.getGoal())
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();
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

            List<ToolEvidence> evidences = result.getEvidences() == null ? List.of() : result.getEvidences();
            List<DiagramPayload> diagrams = result.getDiagrams() == null ? List.of() : result.getDiagrams();

            for (DiagramPayload diagram : diagrams) {
                observer.onDiagram(descriptor, step, diagram);
            }

            if (result.isSuccess()) {
                log.info("工具执行完成: phase={}, toolId={}, evidenceCount={}, diagramCount={}, summary={}",
                        phase,
                        descriptor.getToolId(),
                        evidences.size(),
                        diagrams.size(),
                        result.getSummary());
                observer.onToolResult(descriptor, step, result);
            } else {
                log.warn("工具执行失败: phase={}, toolId={}, error={}",
                        phase,
                        descriptor.getToolId(),
                        result.getErrorMessage());
                observer.onToolError(descriptor, step, result.getErrorMessage());
            }
            return new StepOutcome(trace, evidences, diagrams);
        } catch (Throwable t) {
            trace.setStatus("FAILED");
            trace.setErrorMessage(t.getMessage());
            trace.setCompletedAt(LocalDateTime.now());
            observer.onToolError(descriptor, step, t.getMessage());
            log.warn("工具执行异常: phase={}, toolId={}, reason={}",
                    phase,
                    descriptor.getToolId(),
                    t.getMessage(),
                    t);
            return new StepOutcome(trace, List.of(), List.of());
        }
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

    private record StepOutcome(ToolTraceItem trace,
                               List<ToolEvidence> evidences,
                               List<DiagramPayload> diagrams) {
    }
}
