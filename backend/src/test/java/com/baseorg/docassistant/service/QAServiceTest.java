package com.baseorg.docassistant.service;

import com.baseorg.docassistant.config.AppQaWebSearchProperties;
import com.baseorg.docassistant.dto.qa.*;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionStrategy;
import com.baseorg.docassistant.dto.qa.tool.ToolPlan;
import com.baseorg.docassistant.entity.QAMessage;
import com.baseorg.docassistant.entity.QASession;
import com.baseorg.docassistant.service.qa.*;
import com.baseorg.docassistant.service.qa.tool.EvidenceAssembler;
import com.baseorg.docassistant.service.qa.tool.ToolExecutionService;
import com.baseorg.docassistant.service.qa.tool.ToolPlanningService;
import com.baseorg.docassistant.service.rag.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QAServiceTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private QueryUnderstandingService queryUnderstandingService;
    @Mock
    private HybridRetrievalService hybridRetrievalService;
    @Mock
    private RerankService rerankService;
    @Mock
    private ContextAssembler contextAssembler;
    @Mock
    private AnswerPostProcessor answerPostProcessor;
    @Mock
    private QAAuditService qaAuditService;
    @Mock
    private QARateLimitService rateLimitService;
    @Mock
    private LLMService llmService;
    @Mock
    private ToolPlanningService toolPlanningService;
    @Mock
    private ToolExecutionService toolExecutionService;
    @Mock
    private EvidenceAssembler evidenceAssembler;

    private AppQaWebSearchProperties webSearchProperties;
    private QAService qaService;

    @BeforeEach
    void setUp() {
        webSearchProperties = new AppQaWebSearchProperties();
        qaService = new QAService(
                conversationService,
                queryUnderstandingService,
                hybridRetrievalService,
                rerankService,
                contextAssembler,
                answerPostProcessor,
                qaAuditService,
                rateLimitService,
                llmService,
                toolPlanningService,
                toolExecutionService,
                evidenceAssembler,
                webSearchProperties
        );
        doNothing().when(rateLimitService).check(anyLong());
        when(toolPlanningService.plan(any(), anyList(), anyBoolean())).thenReturn(baseToolPlan());
    }

    @Test
    void shouldReturnNoDocumentFallbackWhenSearchIsEmpty() {
        mockSessionLifecycle();
        when(queryUnderstandingService.plan(anyString(), anyList())).thenReturn(basePlan());
        when(hybridRetrievalService.retrieve(any(), any())).thenReturn(List.of());
        when(rerankService.rerank(anyList(), anyInt())).thenReturn(List.of());
        when(answerPostProcessor.generatePromptHash(anyString(), anyString())).thenReturn("sha256:test");
        when(llmService.generateNoHitFallbackAnswer(anyString(), anyString())).thenReturn("当前知识库暂未命中，我先给你一个通用建议。");

        QAResponse response = qaService.ask(baseRequest(), 1001L);

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getDegradeReason()).isEqualTo("知识库未命中，已使用通用回答");
        assertThat(response.getAnswer()).contains("通用建议");
        assertThat(response.getFallbackMode()).isEqualTo("GENERAL_NO_HIT");
        assertThat(response.getSessionId()).isEqualTo(2001L);
    }

    @Test
    void shouldReturnGeneratedAnswerWhenRetrievalAndLlmSucceed() {
        mockSessionLifecycle();
        when(queryUnderstandingService.plan(anyString(), anyList())).thenReturn(basePlan());
        List<RetrievalCandidate> candidates = List.of(
                RetrievalCandidate.builder().item(resultItem(1L, 11L, "接口定义", "接口使用 OAuth2 鉴权。", 0.9)).vectorScore(0.9).keywordScore(0.4).build()
        );
        List<SearchResult.ResultItem> reranked = List.of(resultItem(1L, 11L, "接口定义", "接口使用 OAuth2 鉴权。", 0.88));
        when(hybridRetrievalService.retrieve(any(), any())).thenReturn(candidates);
        when(rerankService.rerank(anyList(), anyInt())).thenReturn(reranked);
        when(contextAssembler.assemble(anyList(), eq(reranked))).thenReturn("context");
        when(llmService.generateRagAnswer(anyString(), anyString())).thenReturn("这是 AI 生成的答案");
        when(answerPostProcessor.generatePromptHash(anyString(), anyString())).thenReturn("sha256:test");
        when(answerPostProcessor.applyCitationSummary(anyString(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        QAResponse response = qaService.ask(baseRequest(), 1001L);

        assertThat(response.isDegraded()).isFalse();
        assertThat(response.getAnswer()).isEqualTo("这是 AI 生成的答案");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getPromptHash()).isEqualTo("sha256:test");
    }

    @Test
    void shouldReturnSearchFallbackWhenLlmFails() {
        mockSessionLifecycle();
        when(queryUnderstandingService.plan(anyString(), anyList())).thenReturn(basePlan());
        List<SearchResult.ResultItem> reranked = List.of(resultItem(1L, 12L, "部署说明", "系统部署到 Kubernetes 集群。", 0.88));
        when(hybridRetrievalService.retrieve(any(), any())).thenReturn(List.of());
        when(rerankService.rerank(anyList(), anyInt())).thenReturn(reranked);
        when(contextAssembler.assemble(anyList(), eq(reranked))).thenReturn("context");
        when(llmService.generateRagAnswer(anyString(), anyString())).thenReturn(null);
        when(answerPostProcessor.generatePromptHash(anyString(), anyString())).thenReturn("sha256:test");
        when(answerPostProcessor.applyCitationSummary(anyString(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        QAResponse response = qaService.ask(baseRequest(), 1001L);

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getDegradeReason()).isEqualTo("LLM 服务不可用");
        assertThat(response.getAnswer()).contains("根据知识库检索到以下相关内容");
        assertThat(response.getAnswer()).contains("Kubernetes");
        assertThat(response.getFallbackMode()).isEqualTo("RAG_SNIPPET");
    }

    @Test
    void shouldIgnoreAuditFailureAndKeepQaResponseSuccessful() {
        mockSessionLifecycle();
        when(queryUnderstandingService.plan(anyString(), anyList())).thenReturn(basePlan());
        List<SearchResult.ResultItem> reranked = List.of(resultItem(1L, 11L, "接口定义", "接口使用 OAuth2 鉴权。", 0.88));
        when(hybridRetrievalService.retrieve(any(), any())).thenReturn(List.of());
        when(rerankService.rerank(anyList(), anyInt())).thenReturn(reranked);
        when(contextAssembler.assemble(anyList(), eq(reranked))).thenReturn("context");
        when(llmService.generateRagAnswer(anyString(), anyString())).thenReturn("正常答案");
        when(answerPostProcessor.generatePromptHash(anyString(), anyString())).thenReturn("sha256:test");
        when(answerPostProcessor.applyCitationSummary(anyString(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("audit insert failed")).when(qaAuditService)
                .record(anyLong(), anyLong(), anyString(), anyLong(), anyBoolean(), anyString(), anyString(), anyInt(), any(), anyMap());

        QAResponse response = qaService.ask(baseRequest(), 1001L);

        assertThat(response.isDegraded()).isFalse();
        assertThat(response.getAnswer()).isEqualTo("正常答案");
    }

    @Test
    void shouldRouteSmallTalkToGeneralAnswer() {
        mockSessionLifecycle();
        when(queryUnderstandingService.plan(anyString(), anyList())).thenReturn(QueryPlan.builder()
                .intent(QueryPlan.Intent.SMALL_TALK)
                .rawQuestion("hi")
                .rewrittenQuery("hi")
                .historySummary("")
                .shouldSkipRetrieval(true)
                .build());
        when(answerPostProcessor.generatePromptHash(anyString(), anyString())).thenReturn("sha256:test");
        when(llmService.generateGeneralAnswer(anyString(), anyString())).thenReturn("你好，我在。");

        QAResponse response = qaService.ask(baseRequest("hi"), 1001L);

        assertThat(response.getIntent()).isEqualTo("SMALL_TALK");
        assertThat(response.getAnswer()).contains("你好");
        assertThat(response.getSources()).isEmpty();
        assertThat(response.isDegraded()).isFalse();
        assertThat(response.getFallbackMode()).isEqualTo("GENERAL_SMALL_TALK");
    }

    private void mockSessionLifecycle() {
        when(conversationService.ensureSession(eq(1001L), any())).thenReturn(QASession.builder()
                .id(2001L)
                .userId(1001L)
                .title("系统如何鉴权？")
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .build());
        when(conversationService.loadRecentMessages(eq(1001L), eq(2001L), anyInt())).thenReturn(List.of(
                QAMessage.builder()
                        .id(3001L)
                        .sessionId(2001L)
                        .userId(1001L)
                        .role(QAMessage.MessageRole.USER)
                        .content("上一轮问题")
                        .status(QAMessage.MessageStatus.COMPLETED)
                        .build()
        ));
        when(conversationService.appendUserMessage(eq(1001L), eq(2001L), anyString())).thenReturn(QAMessage.builder()
                .id(3002L)
                .sessionId(2001L)
                .userId(1001L)
                .role(QAMessage.MessageRole.USER)
                .content("系统如何鉴权？")
                .status(QAMessage.MessageStatus.COMPLETED)
                .build());
        when(conversationService.appendAssistantPlaceholder(eq(1001L), eq(2001L))).thenReturn(QAMessage.builder()
                .id(3003L)
                .sessionId(2001L)
                .userId(1001L)
                .role(QAMessage.MessageRole.ASSISTANT)
                .content("")
                .status(QAMessage.MessageStatus.PROCESSING)
                .build());
    }

    private QARequest baseRequest() {
        return baseRequest("系统如何鉴权？");
    }

    private QARequest baseRequest(String question) {
        return QARequest.builder()
                .sessionId(2001L)
                .question(question)
                .includeSources(true)
                .topK(5)
                .scoreThreshold(0.7)
                .build();
    }

    private QueryPlan basePlan() {
        return QueryPlan.builder()
                .intent(QueryPlan.Intent.RAG_SEARCH)
                .rawQuestion("系统如何鉴权？")
                .rewrittenQuery("系统如何鉴权？")
                .historySummary("USER: 上一轮问题")
                .shouldSkipRetrieval(false)
                .toolPlanned(false)
                .graphPlanned(false)
                .build();
    }

    private ToolPlan baseToolPlan() {
        return ToolPlan.builder()
                .planId("plan-1")
                .strategy(ToolExecutionStrategy.RAG_ONLY)
                .summary("当前问题优先直接走知识库问答")
                .llmSelected(false)
                .candidateToolIds(List.of())
                .steps(List.of())
                .build();
    }

    private SearchResult.ResultItem resultItem(Long chunkId, Long docId, String sectionTitle, String content, double score) {
        return SearchResult.ResultItem.builder()
                .chunkId(chunkId)
                .docId(docId)
                .docTitle("文档-" + docId)
                .chunkIndex(0)
                .sectionTitle(sectionTitle)
                .content(content)
                .score(score)
                .build();
    }
}
