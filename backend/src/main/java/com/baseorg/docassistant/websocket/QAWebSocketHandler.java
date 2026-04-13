package com.baseorg.docassistant.websocket;

import com.baseorg.docassistant.dto.qa.QAResponse;
import com.baseorg.docassistant.dto.qa.QAWebSocketAskRequest;
import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolPlan;
import com.baseorg.docassistant.dto.qa.tool.ToolTraceItem;
import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.service.QAService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QAWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final QAService qaService;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("缺少用户上下文"));
            return;
        }

        QAWebSocketAskRequest request = objectMapper.readValue(message.getPayload(), QAWebSocketAskRequest.class);
        if (!"ask".equalsIgnoreCase(request.getType())) {
            sendEvent(session, Map.of(
                    "type", "error",
                    "code", "INVALID_MESSAGE",
                    "message", "仅支持 ask 事件"
            ));
            return;
        }

        try {
            qaService.streamAsk(userId, request.getSessionId(), request.getMessageId(), request.getQuestion(), Boolean.TRUE.equals(request.getWebSearchEnabled()), new QAService.QAStreamListener() {
                @Override
                public void onAck(String clientMessageId, Long sessionId) {
                    sendSafely(session, ordered(
                            "type", "ack",
                            "messageId", clientMessageId,
                            "sessionId", sessionId
                    ));
                }

                @Override
                public void onRewrite(String rewrittenQuery, Long assistantMessageId) {
                    sendSafely(session, ordered(
                            "type", "rewrite",
                            "messageId", String.valueOf(assistantMessageId),
                            "rewrittenQuery", rewrittenQuery
                    ));
                }

                @Override
                public void onPlan(String planSummary, ToolPlan toolPlan, Long assistantMessageId) {
                    sendSafely(session, ordered(
                            "type", "plan",
                            "messageId", String.valueOf(assistantMessageId),
                            "planSummary", planSummary,
                            "toolPlan", toolPlan
                    ));
                }

                @Override
                public void onToolStart(ToolTraceItem traceItem, Long assistantMessageId) {
                    sendSafely(session, ordered(
                            "type", "tool_start",
                            "messageId", String.valueOf(assistantMessageId),
                            "trace", traceItem
                    ));
                }

                @Override
                public void onToolResult(ToolTraceItem traceItem, Long assistantMessageId) {
                    sendSafely(session, ordered(
                            "type", "tool_result",
                            "messageId", String.valueOf(assistantMessageId),
                            "trace", traceItem
                    ));
                }

                @Override
                public void onToolError(String toolId, String stepId, String errorMessage, Long assistantMessageId) {
                    sendSafely(session, ordered(
                            "type", "tool_error",
                            "messageId", String.valueOf(assistantMessageId),
                            "toolId", toolId,
                            "stepId", stepId,
                            "message", errorMessage
                    ));
                }

                @Override
                public void onDiagram(DiagramPayload diagram, Long assistantMessageId) {
                    sendSafely(session, ordered(
                            "type", "diagram",
                            "messageId", String.valueOf(assistantMessageId),
                            "diagram", diagram
                    ));
                }

                @Override
                public void onSources(List<QAResponse.SourceChunk> sources, Long assistantMessageId) {
                    sendSafely(session, ordered(
                            "type", "sources",
                            "messageId", String.valueOf(assistantMessageId),
                            "sources", sources
                    ));
                }

                @Override
                public void onDelta(String content, Long assistantMessageId) {
                    sendSafely(session, ordered(
                            "type", "delta",
                            "messageId", String.valueOf(assistantMessageId),
                            "content", content
                    ));
                }

                @Override
                public void onDone(String clientMessageId, QAService.PipelineResult result) {
                    sendSafely(session, ordered(
                            "type", "done",
                            "messageId", String.valueOf(result.assistantMessageId()),
                            "clientMessageId", clientMessageId,
                            "sessionId", result.sessionId(),
                            "answer", result.answer(),
                            "sources", result.sources(),
                            "confidence", result.confidence(),
                            "degraded", result.degraded(),
                            "degradeReason", result.degradeReason(),
                            "intent", result.intent(),
                            "fallbackMode", result.fallbackMode(),
                            "processingTimeMs", result.processingTimeMs(),
                            "promptHash", result.promptHash(),
                            "rewrittenQuery", result.rewrittenQuery(),
                            "planSummary", result.planSummary(),
                            "toolTrace", result.toolTrace(),
                            "diagrams", result.diagrams(),
                            "externalSources", result.externalSources()
                    ));
                }
            });
        } catch (BusinessException e) {
            sendEvent(session, ordered(
                    "type", "error",
                    "code", e.getCode(),
                    "message", e.getMessage(),
                    "messageId", request.getMessageId()
            ));
        }
    }

    private Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            data.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return data;
    }

    private void sendSafely(WebSocketSession session, Map<String, Object> payload) {
        try {
            sendEvent(session, payload);
        } catch (IOException e) {
            log.warn("WebSocket 发送失败: {}", e.getMessage());
        }
    }

    private void sendEvent(WebSocketSession session, Map<String, Object> payload) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        session.sendMessage(new TextMessage(toJson(payload)));
    }

    private String toJson(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }
}
