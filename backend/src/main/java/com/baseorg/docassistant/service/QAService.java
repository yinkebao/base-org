package com.baseorg.docassistant.service;

import com.baseorg.docassistant.config.AppQaWebSearchProperties;
import com.baseorg.docassistant.dto.qa.QARequest;
import com.baseorg.docassistant.dto.qa.QAResponse;
import com.baseorg.docassistant.dto.qa.QueryPlan;
import com.baseorg.docassistant.dto.qa.RetrievalCandidate;
import com.baseorg.docassistant.dto.qa.SearchRequest;
import com.baseorg.docassistant.dto.qa.SearchResult;
import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionBatchResult;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionPhase;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionResult;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionStrategy;
import com.baseorg.docassistant.dto.qa.tool.ToolPlan;
import com.baseorg.docassistant.dto.qa.tool.ToolStep;
import com.baseorg.docassistant.dto.qa.tool.ToolTraceItem;
import com.baseorg.docassistant.dto.qa.tool.WebSearchSummaryMode;
import com.baseorg.docassistant.entity.QAMessage;
import com.baseorg.docassistant.entity.QASession;
import com.baseorg.docassistant.service.qa.AnswerPostProcessor;
import com.baseorg.docassistant.service.qa.ContextAssembler;
import com.baseorg.docassistant.service.qa.ConversationService;
import com.baseorg.docassistant.service.qa.HybridRetrievalService;
import com.baseorg.docassistant.service.qa.QAAuditService;
import com.baseorg.docassistant.service.qa.QARateLimitService;
import com.baseorg.docassistant.service.qa.QueryUnderstandingService;
import com.baseorg.docassistant.service.qa.RerankService;
import com.baseorg.docassistant.service.qa.tool.EvidenceAssembler;
import com.baseorg.docassistant.service.qa.tool.ToolExecutionObserver;
import com.baseorg.docassistant.service.qa.tool.ToolExecutionService;
import com.baseorg.docassistant.service.qa.tool.ToolPlanningService;
import com.baseorg.docassistant.service.rag.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 问答编排服务。
 * <p>
 * 该类负责串联一次完整的 QA 请求生命周期，包括：
 * 会话准备、上下文读取、问题理解、工具规划、工具执行、
 * 混合检索、结果重排、证据组装、LLM 生成、消息落库以及审计记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    private static final String NO_RESULT_ANSWER = "抱歉，我在知识库中没有找到与您问题相关的内容。请尝试换一种方式提问。";
    private static final String FALLBACK_ANSWER = "抱歉，我暂时无法回答您的问题。请稍后再试或联系管理员。";
    private static final String SMALL_TALK_FALLBACK_ANSWER = "你好，我在。你可以直接问我文档、导入、检索或系统配置相关的问题。";
    private static final String NO_HIT_CHAT_FALLBACK_MODE = "GENERAL_NO_HIT";
    private static final String SMALL_TALK_FALLBACK_MODE = "GENERAL_SMALL_TALK";
    private static final String RETRIEVAL_FALLBACK_MODE = "RAG_SNIPPET";
    private static final String TOOL_FALLBACK_MODE = "TOOL_SUMMARY";

    private final ConversationService conversationService;
    private final QueryUnderstandingService queryUnderstandingService;
    private final HybridRetrievalService hybridRetrievalService;
    private final RerankService rerankService;
    private final ContextAssembler contextAssembler;
    private final AnswerPostProcessor answerPostProcessor;
    private final QAAuditService qaAuditService;
    private final QARateLimitService rateLimitService;
    private final LLMService llmService;
    private final ToolPlanningService toolPlanningService;
    private final ToolExecutionService toolExecutionService;
    private final EvidenceAssembler evidenceAssembler;
    private final AppQaWebSearchProperties webSearchProperties;

    /**
     * 同步执行一次问答请求，并返回完整问答结果。
     * <p>
     * 本方法不再声明 {@code @Transactional}：整个 pipeline 会跨越外部 HTTP（联网搜索、MCP、LLM 流式）
     * 与多次 DB 操作，包一层大事务会让数据库连接长时间被外部 IO 占用。实际事务边界已下沉到
     * {@link ConversationService} 与 {@link QAAuditService} 的各具体写方法内，
     * 每次 DB 写入立即提交，外部调用失败时通过 {@code failAssistantMessage} 将占位消息标记为失败。
     */
    public QAResponse ask(QARequest request, Long userId) {
        QASession session = conversationService.ensureSession(userId, request.getSessionId());
        PipelineResult result = executePipeline(userId, session.getId(), request.getQuestion(), request.getTopK(), request.getScoreThreshold(), request.isWebSearchEnabled());

        return QAResponse.builder()
                .answer(result.answer())
                .confidence(result.confidence())
                .sources(result.sources())
                .degraded(result.degraded())
                .degradeReason(result.degradeReason())
                .processingTimeMs(result.processingTimeMs())
                .promptHash(result.promptHash())
                .sessionId(result.sessionId())
                .messageId(result.assistantMessageId())
                .rewrittenQuery(result.rewrittenQuery())
                .intent(result.intent())
                .fallbackMode(result.fallbackMode())
                .timestamp(LocalDateTime.now())
                .planSummary(result.planSummary())
                .toolTrace(result.toolTrace())
                .diagrams(result.diagrams())
                .externalSources(result.externalSources())
                .build();
    }

    /**
     * 以流式方式执行问答。
     */
    public void streamAsk(Long userId,
                          Long sessionId,
                          String clientMessageId,
                          String question,
                          boolean webSearchEnabled,
                          QAStreamListener listener) {
        QASession session = conversationService.ensureSession(userId, sessionId);
        listener.onAck(clientMessageId, session.getId());
        PipelineResult result = executePipeline(userId, session.getId(), question, 5, 0.7, webSearchEnabled, listener);
        listener.onDone(clientMessageId, result);
    }

    private PipelineResult executePipeline(Long userId,
                                           Long sessionId,
                                           String question,
                                           int topK,
                                           double scoreThreshold,
                                           boolean webSearchEnabled) {
        return executePipeline(userId, sessionId, question, topK, scoreThreshold, webSearchEnabled, null);
    }

    private PipelineResult executePipeline(Long userId,
                                           Long sessionId,
                                           String question,
                                           int topK,
                                           double scoreThreshold,
                                           boolean webSearchEnabled,
                                           QAStreamListener listener) {
        long startTime = System.currentTimeMillis();
        rateLimitService.check(userId);

        List<QAMessage> recentMessages = conversationService.loadRecentMessages(userId, sessionId, 6);
        conversationService.appendUserMessage(userId, sessionId, question);
        QAMessage assistantMessage = conversationService.appendAssistantPlaceholder(userId, sessionId);

        try {
            QueryPlan queryPlan = queryUnderstandingService.plan(question, recentMessages);
            log.info("QA 问题理解完成: sessionId={}, intent={}, rewrittenQuery={}, skipRetrieval={}, webSearchEnabled={}",
                    sessionId,
                    queryPlan.getIntent(),
                    queryPlan.getRewrittenQuery(),
                    queryPlan.isShouldSkipRetrieval(),
                    webSearchEnabled);
            if (!queryPlan.getRewrittenQuery().equals(question) && listener != null) {
                listener.onRewrite(queryPlan.getRewrittenQuery(), assistantMessage.getId());
            }

            ToolPlan toolPlan = toolPlanningService.plan(queryPlan, recentMessages, webSearchEnabled);
            log.info("QA 工具规划完成: sessionId={}, strategy={}, stepCount={}, summary={}, candidateToolIds={}",
                    sessionId,
                    toolPlan.getStrategy(),
                    toolPlan.getSteps() == null ? 0 : toolPlan.getSteps().size(),
                    toolPlan.getSummary(),
                    toolPlan.getCandidateToolIds());
            if (listener != null && toolPlan != null && toolPlan.getSteps() != null && !toolPlan.getSteps().isEmpty()) {
                listener.onPlan(toolPlan.getSummary(), toolPlan, assistantMessage.getId());
            }

            if (queryPlan.isShouldSkipRetrieval()) {
                return handleSmallTalk(userId, sessionId, question, assistantMessage, listener, startTime, queryPlan, toolPlan, webSearchEnabled);
            }

            List<ToolTraceItem> toolTrace = new ArrayList<>();
            List<ToolEvidence> toolEvidences = new ArrayList<>();
            List<DiagramPayload> diagrams = new ArrayList<>();

            if (toolPlan.getStrategy() == ToolExecutionStrategy.TOOL_ONLY
                    || toolPlan.getStrategy() == ToolExecutionStrategy.TOOL_THEN_RAG) {
                ToolExecutionBatchResult preBatch = executeToolPhase(
                        toolPlan,
                        ToolExecutionPhase.PRE_RETRIEVAL,
                        false,
                        userId,
                        sessionId,
                        queryPlan.getRewrittenQuery(),
                        queryPlan,
                        toolEvidences,
                        List.of(),
                        assistantMessage.getId(),
                        listener
                );
                mergeToolBatch(preBatch, toolTrace, toolEvidences, diagrams);
            }

            List<SearchResult.ResultItem> reranked = List.of();
            List<QAResponse.SourceChunk> sources = List.of();
            if (toolPlan.getStrategy() != ToolExecutionStrategy.TOOL_ONLY) {
                SearchRequest searchRequest = SearchRequest.builder()
                        .query(queryPlan.getRewrittenQuery())
                        .topK(Math.max(topK, 1))
                        .scoreThreshold(scoreThreshold)
                        .userId(userId)
                        .build();

                List<RetrievalCandidate> retrieved = hybridRetrievalService.retrieve(queryPlan, searchRequest);
                reranked = rerankService.rerank(retrieved, Math.max(topK, 1));
                sources = reranked.stream()
                        .limit(4)
                        .map(this::toSourceChunk)
                        .toList();
                log.info("QA 检索阶段完成: sessionId={}, retrievedCount={}, rerankedCount={}, avgScore={}, topScore={}",
                        sessionId,
                        retrieved.size(),
                        reranked.size(),
                        reranked.isEmpty() ? 0D : calculateConfidence(reranked),
                        reranked.isEmpty() ? 0D : reranked.get(0).getScore());
                if (listener != null) {
                    listener.onSources(sources, assistantMessage.getId());
                }
            }

            boolean retrievalHit = !reranked.isEmpty();
            boolean shouldTriggerWebSearch = shouldTriggerWebSearchFallback(webSearchEnabled, reranked);
            log.info("QA 联网搜索判定: sessionId={}, retrievalHit={}, shouldTriggerWebSearch={}, detail={}",
                    sessionId,
                    retrievalHit,
                    shouldTriggerWebSearch,
                    describeWebSearchDecision(webSearchEnabled, reranked));
            ToolExecutionBatchResult postBatch = executeToolPhase(
                    toolPlan,
                    ToolExecutionPhase.POST_RETRIEVAL,
                    shouldTriggerWebSearch,
                    userId,
                    sessionId,
                    queryPlan.getRewrittenQuery(),
                    queryPlan,
                    toolEvidences,
                    reranked,
                    assistantMessage.getId(),
                    listener
            );
            mergeToolBatch(postBatch, toolTrace, toolEvidences, diagrams);

            if (!retrievalHit && toolPlan.getStrategy() != ToolExecutionStrategy.TOOL_ONLY && toolEvidences.isEmpty()) {
                return handleNoRetrievalHit(userId, sessionId, question, assistantMessage, listener, startTime, queryPlan,
                        toolPlan, toolTrace, diagrams, webSearchEnabled);
            }

            String combinedContext = buildCombinedContext(recentMessages, reranked, toolEvidences, diagrams);
            List<ToolEvidence> externalSources = extractExternalSources(toolEvidences);
            String promptHash = answerPostProcessor.generatePromptHash(queryPlan.getRewrittenQuery(), combinedContext);
            AtomicReference<String> answerRef = new AtomicReference<>("");

            generateAnswer(queryPlan.getRewrittenQuery(), reranked, toolEvidences, combinedContext, toolPlan.getSummary(), listener,
                    assistantMessage.getId(), answerRef);

            boolean degraded = false;
            String degradeReason = null;
            String fallbackMode = null;
            String answer = answerRef.get();

            if (answer == null || answer.isBlank()) {
                degraded = true;
                if (!toolEvidences.isEmpty()) {
                    degradeReason = "工具已返回结果，但 LLM 未生成回答";
                    fallbackMode = TOOL_FALLBACK_MODE;
                    answer = buildToolFallbackAnswer(toolEvidences, diagrams);
                } else {
                    degradeReason = "LLM 服务不可用";
                    fallbackMode = RETRIEVAL_FALLBACK_MODE;
                    answer = buildFallbackAnswer(reranked);
                }
            }

            answer = answerPostProcessor.applyCitationSummary(answer, sources);
            double confidence = !reranked.isEmpty() ? calculateConfidence(reranked) : calculateToolConfidence(toolEvidences, diagrams);
            long processingTime = System.currentTimeMillis() - startTime;

            conversationService.completeAssistantMessage(
                    assistantMessage,
                    answer,
                    queryPlan.getIntent().name(),
                    queryPlan.getRewrittenQuery(),
                    sources,
                    confidence,
                    degraded,
                    degradeReason,
                    fallbackMode,
                    processingTime,
                    promptHash,
                    toolPlan.getSummary(),
                    toolTrace,
                    diagrams,
                    externalSources
            );
            safeRecordAudit(userId, sessionId, promptHash, processingTime, degraded, queryPlan.getRewrittenQuery(),
                    queryPlan.getIntent().name(), reranked.size(), fallbackMode,
                    buildAuditDetails(webSearchEnabled, shouldTriggerWebSearch, externalSources));

            return new PipelineResult(
                    sessionId,
                    assistantMessage.getId(),
                    answer,
                    confidence,
                    sources,
                    degraded,
                    degradeReason,
                    processingTime,
                    promptHash,
                    queryPlan.getRewrittenQuery(),
                    queryPlan.getIntent().name(),
                    fallbackMode,
                    toolPlan.getSummary(),
                    toolTrace,
                    diagrams,
                    externalSources
            );
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            conversationService.failAssistantMessage(assistantMessage, "INTERNAL_ERROR", processingTime);
            log.error("问答处理失败: {}", e.getMessage(), e);
            return new PipelineResult(
                    sessionId,
                    assistantMessage.getId(),
                    FALLBACK_ANSWER,
                    0.0,
                    List.of(),
                    true,
                    "系统异常: " + e.getMessage(),
                    processingTime,
                    answerPostProcessor.generatePromptHash(question, ""),
                    question,
                    QueryPlan.Intent.RAG_SEARCH.name(),
                    "SYSTEM_ERROR",
                    null,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    private ToolExecutionBatchResult executeToolPhase(ToolPlan toolPlan,
                                                      ToolExecutionPhase phase,
                                                      boolean allowFallbackTools,
                                                      Long userId,
                                                      Long sessionId,
                                                      String question,
                                                      QueryPlan queryPlan,
                                                      List<ToolEvidence> accumulatedEvidence,
                                                      List<SearchResult.ResultItem> retrievalItems,
                                                      Long assistantMessageId,
                                                      QAStreamListener listener) {
        if (toolPlan == null || toolPlan.getSteps() == null || toolPlan.getSteps().isEmpty()) {
            return emptyToolBatch();
        }
        List<ToolStep> selectedSteps = toolPlan.getSteps().stream()
                .filter(step -> step.getExecutionPhase() == phase)
                .filter(step -> allowFallbackTools || !step.isFallbackOnly())
                .toList();
        log.info("QA 工具阶段筛选: sessionId={}, phase={}, allowFallbackTools={}, selectedSteps={}",
                sessionId,
                phase,
                allowFallbackTools,
                selectedSteps.stream().map(ToolStep::getToolId).toList());
        if (selectedSteps.isEmpty()) {
            return emptyToolBatch();
        }

        ToolPlan phasePlan = ToolPlan.builder()
                .planId(toolPlan.getPlanId())
                .strategy(toolPlan.getStrategy())
                .summary(toolPlan.getSummary())
                .llmSelected(toolPlan.isLlmSelected())
                .candidateToolIds(toolPlan.getCandidateToolIds())
                .steps(selectedSteps)
                .build();

        return toolExecutionService.execute(
                phasePlan,
                phase,
                userId,
                sessionId,
                question,
                queryPlan,
                accumulatedEvidence,
                retrievalItems,
                buildToolObserver(listener, assistantMessageId)
        );
    }

    private ToolExecutionObserver buildToolObserver(QAStreamListener listener, Long assistantMessageId) {
        if (listener == null) {
            return new ToolExecutionObserver() {
            };
        }
        return new ToolExecutionObserver() {
            @Override
            public void onToolStart(ToolDescriptor descriptor, ToolStep step) {
                listener.onToolStart(ToolTraceItem.builder()
                        .stepId(step.getStepId())
                        .toolId(step.getToolId())
                        .toolName(descriptor == null ? step.getToolId() : descriptor.getDisplayName())
                        .goal(step.getGoal())
                        .status("RUNNING")
                        .startedAt(LocalDateTime.now())
                        .build(), assistantMessageId);
            }

            @Override
            public void onToolResult(ToolDescriptor descriptor, ToolStep step, ToolExecutionResult result) {
                listener.onToolResult(ToolTraceItem.builder()
                        .stepId(step.getStepId())
                        .toolId(step.getToolId())
                        .toolName(descriptor.getDisplayName())
                        .goal(step.getGoal())
                        .status(result.isSuccess() ? "SUCCESS" : "FAILED")
                        .summary(result.getSummary())
                        .errorMessage(result.getErrorMessage())
                        .completedAt(LocalDateTime.now())
                        .build(), assistantMessageId);
            }

            @Override
            public void onToolError(ToolDescriptor descriptor, ToolStep step, String message) {
                listener.onToolError(step.getToolId(), step.getStepId(), message, assistantMessageId);
            }

            @Override
            public void onDiagram(ToolDescriptor descriptor, ToolStep step, DiagramPayload diagram) {
                listener.onDiagram(diagram, assistantMessageId);
            }
        };
    }

    private ToolExecutionBatchResult emptyToolBatch() {
        return ToolExecutionBatchResult.builder()
                .traceItems(List.of())
                .evidences(List.of())
                .diagrams(List.of())
                .build();
    }

    private void mergeToolBatch(ToolExecutionBatchResult batch,
                                List<ToolTraceItem> toolTrace,
                                List<ToolEvidence> toolEvidences,
                                List<DiagramPayload> diagrams) {
        if (batch == null) {
            return;
        }
        if (batch.getTraceItems() != null) {
            toolTrace.addAll(batch.getTraceItems());
        }
        if (batch.getEvidences() != null) {
            toolEvidences.addAll(batch.getEvidences());
        }
        if (batch.getDiagrams() != null) {
            diagrams.addAll(batch.getDiagrams());
        }
    }

    private void generateAnswer(String rewrittenQuery,
                                List<SearchResult.ResultItem> reranked,
                                List<ToolEvidence> toolEvidences,
                                String combinedContext,
                                String planSummary,
                                QAStreamListener listener,
                                Long assistantMessageId,
                                AtomicReference<String> answerRef) {
        boolean useToolAwarePrompt = !toolEvidences.isEmpty();
        WebSearchSummaryMode summaryMode = resolveSummaryMode(toolEvidences);
        if (listener != null) {
            if (useToolAwarePrompt) {
                llmService.generateToolAwareAnswerStream(rewrittenQuery, combinedContext, planSummary, summaryMode)
                        .doOnNext(chunk -> {
                            answerRef.set(answerRef.get() + chunk);
                            listener.onDelta(chunk, assistantMessageId);
                        })
                        .blockLast();
                return;
            }
            llmService.generateRagAnswerStream(rewrittenQuery, combinedContext)
                    .doOnNext(chunk -> {
                        answerRef.set(answerRef.get() + chunk);
                        listener.onDelta(chunk, assistantMessageId);
                    })
                    .blockLast();
            return;
        }

        if (useToolAwarePrompt) {
            answerRef.set(llmService.generateToolAwareAnswer(rewrittenQuery, combinedContext, planSummary, summaryMode));
            return;
        }
        if (!reranked.isEmpty()) {
            answerRef.set(llmService.generateRagAnswer(rewrittenQuery, combinedContext));
            return;
        }
        answerRef.set(llmService.generateGeneralAnswer(rewrittenQuery, planSummary));
    }

    /**
     * 从工具证据中提取本轮回答模式。
     * <p>
     * 联网搜索工具会把识别到的 summaryMode 放进 metadata，回答阶段优先复用，
     * 避免同一轮问答里前后使用了不同的输出风格。
     */
    private WebSearchSummaryMode resolveSummaryMode(List<ToolEvidence> toolEvidences) {
        if (toolEvidences == null || toolEvidences.isEmpty()) {
            return WebSearchSummaryMode.NARRATIVE_SUMMARY;
        }
        return toolEvidences.stream()
                .map(ToolEvidence::getMetadata)
                .filter(Objects::nonNull)
                .map(metadata -> metadata.get("summaryMode"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .findFirst()
                .map(value -> {
                    try {
                        return WebSearchSummaryMode.valueOf(value);
                    } catch (IllegalArgumentException e) {
                        return WebSearchSummaryMode.NARRATIVE_SUMMARY;
                    }
                })
                .orElse(WebSearchSummaryMode.NARRATIVE_SUMMARY);
    }

    /**
     * 统一拼接 prompt 上下文：工具证据 + 历史/检索 + 图表说明。
     * <p>
     * 历史对话与知识库检索片段由 {@link ContextAssembler} 组装，工具证据由
     * {@link EvidenceAssembler} 组装，各司其职，避免同一批 reranked 结果在 prompt 中出现两次。
     */
    private String buildCombinedContext(List<QAMessage> recentMessages,
                                        List<SearchResult.ResultItem> reranked,
                                        List<ToolEvidence> toolEvidences,
                                        List<DiagramPayload> diagrams) {
        String retrievalContext = reranked == null || reranked.isEmpty()
                ? ""
                : nullToEmpty(contextAssembler.assemble(recentMessages, reranked));
        String toolContext = nullToEmpty(evidenceAssembler.assembleToolContext(toolEvidences));
        String diagramContext = diagrams == null || diagrams.isEmpty()
                ? ""
                : "已生成图表：\n" + diagrams.stream()
                .map(diagram -> "- %s (%s)".formatted(diagram.getTitle(), diagram.getDiagramType()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        StringBuilder sb = new StringBuilder();
        if (!toolContext.isBlank()) {
            sb.append(toolContext).append("\n");
        }
        if (!retrievalContext.isBlank()) {
            sb.append(retrievalContext).append("\n");
        }
        if (!diagramContext.isBlank()) {
            sb.append(diagramContext);
        }
        return sb.toString().trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private PipelineResult handleSmallTalk(Long userId,
                                           Long sessionId,
                                           String question,
                                           QAMessage assistantMessage,
                                           QAStreamListener listener,
                                           long startTime,
                                           QueryPlan queryPlan,
                                           ToolPlan toolPlan,
                                           boolean webSearchEnabled) {
        String promptHash = answerPostProcessor.generatePromptHash(question, queryPlan.getHistorySummary());
        AtomicReference<String> answerRef = new AtomicReference<>("");
        if (listener != null) {
            llmService.generateGeneralAnswerStream(question, queryPlan.getHistorySummary())
                    .doOnNext(chunk -> {
                        answerRef.set(answerRef.get() + chunk);
                        listener.onDelta(chunk, assistantMessage.getId());
                    })
                    .blockLast();
        } else {
            answerRef.set(llmService.generateGeneralAnswer(question, queryPlan.getHistorySummary()));
        }

        String answer = answerRef.get();
        if (answer == null || answer.isBlank()) {
            answer = SMALL_TALK_FALLBACK_ANSWER;
        }
        long processingTime = System.currentTimeMillis() - startTime;
        conversationService.completeAssistantMessage(
                assistantMessage,
                answer,
                queryPlan.getIntent().name(),
                queryPlan.getRewrittenQuery(),
                List.of(),
                0.2,
                false,
                null,
                SMALL_TALK_FALLBACK_MODE,
                processingTime,
                promptHash,
                toolPlan == null ? null : toolPlan.getSummary(),
                List.of(),
                List.of(),
                List.of()
        );
        safeRecordAudit(userId, sessionId, promptHash, processingTime, false, queryPlan.getRewrittenQuery(),
                queryPlan.getIntent().name(), 0, SMALL_TALK_FALLBACK_MODE, Map.of("webSearchEnabled", webSearchEnabled));
        return new PipelineResult(
                sessionId,
                assistantMessage.getId(),
                answer,
                0.2,
                List.of(),
                false,
                null,
                processingTime,
                promptHash,
                queryPlan.getRewrittenQuery(),
                queryPlan.getIntent().name(),
                SMALL_TALK_FALLBACK_MODE,
                toolPlan == null ? null : toolPlan.getSummary(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private PipelineResult handleNoRetrievalHit(Long userId,
                                                Long sessionId,
                                                String question,
                                                QAMessage assistantMessage,
                                                QAStreamListener listener,
                                                long startTime,
                                                QueryPlan queryPlan,
                                                ToolPlan toolPlan,
                                                List<ToolTraceItem> toolTrace,
                                                List<DiagramPayload> diagrams,
                                                boolean webSearchEnabled) {
        String promptHash = answerPostProcessor.generatePromptHash(question, queryPlan.getHistorySummary());
        AtomicReference<String> answerRef = new AtomicReference<>("");
        if (listener != null) {
            llmService.generateNoHitFallbackAnswerStream(question, queryPlan.getHistorySummary())
                    .doOnNext(chunk -> {
                        answerRef.set(answerRef.get() + chunk);
                        listener.onDelta(chunk, assistantMessage.getId());
                    })
                    .blockLast();
        } else {
            answerRef.set(llmService.generateNoHitFallbackAnswer(question, queryPlan.getHistorySummary()));
        }

        String answer = answerRef.get();
        boolean degraded = true;
        String degradeReason;
        String fallbackMode;
        if (answer == null || answer.isBlank()) {
            answer = NO_RESULT_ANSWER;
            degradeReason = "未找到相关文档";
            fallbackMode = null;
        } else {
            degradeReason = "知识库未命中，已使用通用回答";
            fallbackMode = NO_HIT_CHAT_FALLBACK_MODE;
        }

        long processingTime = System.currentTimeMillis() - startTime;
        conversationService.completeAssistantMessage(
                assistantMessage,
                answer,
                queryPlan.getIntent().name(),
                queryPlan.getRewrittenQuery(),
                List.of(),
                0.0,
                degraded,
                degradeReason,
                fallbackMode,
                processingTime,
                promptHash,
                toolPlan == null ? null : toolPlan.getSummary(),
                toolTrace,
                diagrams,
                List.of()
        );
        safeRecordAudit(userId, sessionId, promptHash, processingTime, degraded, queryPlan.getRewrittenQuery(),
                queryPlan.getIntent().name(), 0, fallbackMode, Map.of("webSearchEnabled", webSearchEnabled));
        return new PipelineResult(
                sessionId,
                assistantMessage.getId(),
                answer,
                0.0,
                List.of(),
                degraded,
                degradeReason,
                processingTime,
                promptHash,
                queryPlan.getRewrittenQuery(),
                queryPlan.getIntent().name(),
                fallbackMode,
                toolPlan == null ? null : toolPlan.getSummary(),
                toolTrace,
                diagrams,
                List.of()
        );
    }

    private String buildFallbackAnswer(List<SearchResult.ResultItem> items) {
        StringBuilder sb = new StringBuilder("根据知识库检索到以下相关内容：\n\n");
        for (int i = 0; i < Math.min(items.size(), 3); i++) {
            SearchResult.ResultItem item = items.get(i);
            sb.append(i + 1).append(". ").append(item.getDocTitle()).append("\n")
                    .append(truncate(item.getContent(), 200))
                    .append("\n\n");
        }
        sb.append("_（注：此为检索结果，非 AI 生成回答）_");
        return sb.toString();
    }

    private String buildToolFallbackAnswer(List<ToolEvidence> evidences, List<DiagramPayload> diagrams) {
        StringBuilder sb = new StringBuilder("根据已执行的工具结果，我先整理出以下信息：\n\n");
        for (int i = 0; i < Math.min(evidences.size(), 3); i++) {
            ToolEvidence evidence = evidences.get(i);
            sb.append(i + 1).append(". 【").append(evidence.getToolName()).append("】")
                    .append(evidence.getTitle()).append("\n")
                    .append(truncate(evidence.getContent(), 220)).append("\n\n");
        }
        if (diagrams != null && !diagrams.isEmpty()) {
            sb.append("已生成 Mermaid 图表：");
            sb.append(diagrams.stream().map(DiagramPayload::getTitle).reduce((left, right) -> left + "、" + right).orElse("图表"));
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private double calculateConfidence(List<SearchResult.ResultItem> items) {
        return items.stream().limit(3).mapToDouble(SearchResult.ResultItem::getScore).average().orElse(0.0);
    }

    private double calculateToolConfidence(List<ToolEvidence> evidences, List<DiagramPayload> diagrams) {
        if (evidences != null && !evidences.isEmpty()) {
            return 0.72;
        }
        if (diagrams != null && !diagrams.isEmpty()) {
            return 0.48;
        }
        return 0.0;
    }

    private QAResponse.SourceChunk toSourceChunk(SearchResult.ResultItem item) {
        return QAResponse.SourceChunk.builder()
                .chunkId(item.getChunkId())
                .docId(item.getDocId())
                .docTitle(item.getDocTitle())
                .content(truncate(item.getContent(), 220))
                .score(item.getScore())
                .chunkIndex(item.getChunkIndex())
                .metadata(item.getMetadata())
                .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void safeRecordAudit(Long userId,
                                 Long sessionId,
                                 String promptHash,
                                 long processingTimeMs,
                                 boolean degraded,
                                 String rewrittenQuery,
                                 String intent,
                                 int retrievalHitCount,
                                 String fallbackMode,
                                 Map<String, Object> extraDetails) {
        try {
            qaAuditService.record(userId, sessionId, promptHash, processingTimeMs, degraded, rewrittenQuery, intent, retrievalHitCount, fallbackMode, extraDetails);
        } catch (Exception e) {
            log.warn("QA 审计写入失败，已忽略: sessionId={}, reason={}", sessionId, e.getMessage());
        }
    }

    private boolean shouldTriggerWebSearchFallback(boolean webSearchEnabled, List<SearchResult.ResultItem> reranked) {
        if (!webSearchEnabled || !webSearchProperties.isEnabled()) {
            return false;
        }
        if (reranked == null || reranked.isEmpty()) {
            return true;
        }
        double average = calculateConfidence(reranked);
        double topScore = reranked.get(0).getScore();
        long reliableCount = reranked.stream()
                .filter(item -> item.getScore() >= webSearchProperties.getLowConfidenceThreshold())
                .limit(webSearchProperties.getMinReliableSources())
                .count();
        return average < webSearchProperties.getLowConfidenceThreshold()
                || topScore < webSearchProperties.getTopScoreThreshold()
                || reliableCount < webSearchProperties.getMinReliableSources();
    }

    private String describeWebSearchDecision(boolean webSearchEnabled, List<SearchResult.ResultItem> reranked) {
        if (!webSearchEnabled) {
            return "前端未开启联网搜索";
        }
        if (!webSearchProperties.isEnabled()) {
            return "后端联网搜索开关关闭";
        }
        if (reranked == null || reranked.isEmpty()) {
            return "知识库未命中，触发联网兜底";
        }
        double average = calculateConfidence(reranked);
        double topScore = reranked.get(0).getScore();
        long reliableCount = reranked.stream()
                .filter(item -> item.getScore() >= webSearchProperties.getLowConfidenceThreshold())
                .limit(webSearchProperties.getMinReliableSources())
                .count();
        return "avgScore=%.4f, topScore=%.4f, reliableCount=%d, lowThreshold=%.4f, topThreshold=%.4f, minReliable=%d"
                .formatted(
                        average,
                        topScore,
                        reliableCount,
                        webSearchProperties.getLowConfidenceThreshold(),
                        webSearchProperties.getTopScoreThreshold(),
                        webSearchProperties.getMinReliableSources()
                );
    }

    private List<ToolEvidence> extractExternalSources(List<ToolEvidence> toolEvidences) {
        if (toolEvidences == null || toolEvidences.isEmpty()) {
            return List.of();
        }
        return toolEvidences.stream()
                .filter(item -> "WEB_SEARCH".equalsIgnoreCase(item.getSourceType()))
                .toList();
    }

    private Map<String, Object> buildAuditDetails(boolean webSearchEnabled,
                                                  boolean shouldTriggerWebSearch,
                                                  List<ToolEvidence> externalSources) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("webSearchEnabled", webSearchEnabled);
        details.put("webSearchTriggered", shouldTriggerWebSearch);
        details.put("externalSourceCount", externalSources.size());
        details.put("externalDomains", externalSources.stream()
                .map(item -> String.valueOf(item.getMetadata().getOrDefault("domain", "")))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList());
        ToolEvidence firstExternal = externalSources.isEmpty() ? null : externalSources.get(0);
        if (firstExternal != null && firstExternal.getMetadata() != null) {
            details.put("externalProvider", firstExternal.getMetadata().get("externalProvider"));
            details.put("mcpServer", firstExternal.getMetadata().get("mcpServer"));
            details.put("queryVariants", firstExternal.getMetadata().get("queryVariants"));
            details.put("acceptedDomains", firstExternal.getMetadata().get("acceptedDomains"));
            details.put("candidateCount", firstExternal.getMetadata().get("candidateCount"));
        }
        return details;
    }

    /**
     * 流式问答监听器。
     */
    public interface QAStreamListener {
        void onAck(String clientMessageId, Long sessionId);

        void onRewrite(String rewrittenQuery, Long assistantMessageId);

        void onPlan(String planSummary, ToolPlan toolPlan, Long assistantMessageId);

        void onToolStart(ToolTraceItem traceItem, Long assistantMessageId);

        void onToolResult(ToolTraceItem traceItem, Long assistantMessageId);

        void onToolError(String toolId, String stepId, String errorMessage, Long assistantMessageId);

        void onDiagram(DiagramPayload diagram, Long assistantMessageId);

        void onSources(List<QAResponse.SourceChunk> sources, Long assistantMessageId);

        void onDelta(String content, Long assistantMessageId);

        void onDone(String clientMessageId, PipelineResult result);
    }

    /**
     * QA 流水线统一返回对象。
     */
    public record PipelineResult(Long sessionId,
                                 Long assistantMessageId,
                                 String answer,
                                 double confidence,
                                 List<QAResponse.SourceChunk> sources,
                                 boolean degraded,
                                 String degradeReason,
                                 long processingTimeMs,
                                 String promptHash,
                                 String rewrittenQuery,
                                 String intent,
                                 String fallbackMode,
                                 String planSummary,
                                 List<ToolTraceItem> toolTrace,
                                 List<DiagramPayload> diagrams,
                                 List<ToolEvidence> externalSources) {
    }
}
