package com.baseorg.docassistant.service.qa;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baseorg.docassistant.dto.qa.QAMessageResponse;
import com.baseorg.docassistant.dto.qa.QASessionResponse;
import com.baseorg.docassistant.dto.qa.QAResponse;
import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolEvidence;
import com.baseorg.docassistant.dto.qa.tool.ToolTraceItem;
import com.baseorg.docassistant.entity.QAMessage;
import com.baseorg.docassistant.entity.QASession;
import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.exception.ErrorCode;
import com.baseorg.docassistant.mapper.QAMessageMapper;
import com.baseorg.docassistant.mapper.QASessionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话与消息持久化服务
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final QASessionMapper qaSessionMapper;
    private final QAMessageMapper qaMessageMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public QASession ensureSession(Long userId, Long sessionId) {
        if (sessionId == null) {
            QASession session = QASession.builder()
                    .userId(userId)
                    .title("新对话")
                    .lastMessageAt(LocalDateTime.now())
                    .build();
            qaSessionMapper.insert(session);
            return session;
        }

        QASession session = qaSessionMapper.selectOne(new LambdaQueryWrapper<QASession>()
                .eq(QASession::getId, sessionId)
                .eq(QASession::getUserId, userId)
                .last("LIMIT 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在或无权访问");
        }
        return session;
    }

    @Transactional
    public QASession createSession(Long userId) {
        return ensureSession(userId, null);
    }

    @Transactional(readOnly = true)
    public List<QASessionResponse> listSessions(Long userId) {
        return qaSessionMapper.findRecentByUserId(userId, 30).stream()
                .map(session -> QASessionResponse.builder()
                        .sessionId(session.getId())
                        .title(session.getTitle())
                        .lastMessageAt(session.getLastMessageAt())
                        .createdAt(session.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QAMessageResponse> listMessages(Long userId, Long sessionId) {
        ensureSession(userId, sessionId);
        return qaMessageMapper.findBySessionId(sessionId, userId, 100).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QAMessage> loadRecentMessages(Long userId, Long sessionId, int limit) {
        ensureSession(userId, sessionId);
        List<QAMessage> messages = qaMessageMapper.findBySessionId(sessionId, userId, Math.max(limit, 1));
        int from = Math.max(messages.size() - limit, 0);
        return messages.subList(from, messages.size());
    }

    @Transactional
    public QAMessage appendUserMessage(Long userId, Long sessionId, String content) {
        QASession session = ensureSession(userId, sessionId);
        QAMessage message = QAMessage.builder()
                .sessionId(session.getId())
                .userId(userId)
                .role(QAMessage.MessageRole.USER)
                .content(content)
                .status(QAMessage.MessageStatus.COMPLETED)
                .build();
        qaMessageMapper.insert(message);
        touchSession(session, content);
        return message;
    }

    @Transactional
    public QAMessage appendAssistantPlaceholder(Long userId, Long sessionId) {
        QASession session = ensureSession(userId, sessionId);
        QAMessage message = QAMessage.builder()
                .sessionId(session.getId())
                .userId(userId)
                .role(QAMessage.MessageRole.ASSISTANT)
                .content("")
                .status(QAMessage.MessageStatus.PROCESSING)
                .build();
        qaMessageMapper.insert(message);
        touchSession(session, null);
        return message;
    }

    @Transactional
    public void completeAssistantMessage(QAMessage assistantMessage,
                                         String content,
                                         String intent,
                                         String rewrittenQuery,
                                         List<QAResponse.SourceChunk> sources,
                                         double confidence,
                                         boolean degraded,
                                         String degradeReason,
                                         String fallbackMode,
                                         long processingTimeMs,
                                         String promptHash,
                                         String planSummary,
                                         List<ToolTraceItem> toolTrace,
                                         List<DiagramPayload> diagrams,
                                         List<ToolEvidence> externalSources) {
        assistantMessage.setContent(content);
        assistantMessage.setIntent(intent);
        assistantMessage.setRewrittenQuery(rewrittenQuery);
        assistantMessage.setSourcesJson(writeSources(sources));
        assistantMessage.setConfidence(confidence);
        assistantMessage.setDegraded(degraded);
        assistantMessage.setDegradeReason(degradeReason);
        assistantMessage.setFallbackMode(fallbackMode);
        assistantMessage.setPromptHash(promptHash);
        assistantMessage.setStatus(QAMessage.MessageStatus.COMPLETED);
        assistantMessage.setProcessingTimeMs(processingTimeMs);
        assistantMessage.setPlanSummary(planSummary);
        assistantMessage.setToolTraceJson(writeObjects(toolTrace));
        assistantMessage.setDiagramsJson(writeObjects(diagrams));
        assistantMessage.setExternalSourcesJson(writeObjects(externalSources));
        qaMessageMapper.updateById(assistantMessage);
    }

    @Transactional
    public void failAssistantMessage(QAMessage assistantMessage, String errorCode, long processingTimeMs) {
        assistantMessage.setStatus(QAMessage.MessageStatus.FAILED);
        assistantMessage.setErrorCode(errorCode);
        assistantMessage.setProcessingTimeMs(processingTimeMs);
        qaMessageMapper.updateById(assistantMessage);
    }

    private void touchSession(QASession session, String firstQuestion) {
        session.setLastMessageAt(LocalDateTime.now());
        if ((session.getTitle() == null || "新对话".equals(session.getTitle()))
                && firstQuestion != null && !firstQuestion.isBlank()) {
            session.setTitle(buildTitle(firstQuestion));
        }
        qaSessionMapper.updateById(session);
    }

    private String buildTitle(String question) {
        String normalized = question.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 20 ? normalized : normalized.substring(0, 20) + "...";
    }

    private QAMessageResponse toMessageResponse(QAMessage message) {
        return QAMessageResponse.builder()
                .messageId(message.getId())
                .role(message.getRole().name().toLowerCase())
                .content(message.getContent())
                .rewrittenQuery(message.getRewrittenQuery())
                .sources(readSources(message.getSourcesJson()))
                .confidence(message.getConfidence())
                .promptHash(message.getPromptHash())
                .intent(message.getIntent())
                .status(message.getStatus().name())
                .degraded(Boolean.TRUE.equals(message.getDegraded()))
                .degradeReason(message.getDegradeReason())
                .fallbackMode(message.getFallbackMode())
                .errorCode(message.getErrorCode())
                .processingTimeMs(message.getProcessingTimeMs())
                .planSummary(message.getPlanSummary())
                .toolTrace(readObjects(message.getToolTraceJson(), new TypeReference<List<ToolTraceItem>>() {}))
                .diagrams(readObjects(message.getDiagramsJson(), new TypeReference<List<DiagramPayload>>() {}))
                .externalSources(readObjects(message.getExternalSourcesJson(), new TypeReference<List<ToolEvidence>>() {}))
                .createdAt(message.getCreatedAt())
                .build();
    }

    private List<Map<String, Object>> writeSources(List<QAResponse.SourceChunk> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(sources, new TypeReference<List<Map<String, Object>>>() {});
    }

    private List<QAResponse.SourceChunk> readSources(List<Map<String, Object>> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return objectMapper.convertValue(value, new TypeReference<List<QAResponse.SourceChunk>>() {});
    }

    private List<Map<String, Object>> writeObjects(List<?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() {});
    }

    private <T> T readObjects(List<Map<String, Object>> value, TypeReference<T> typeReference) {
        if (value == null || value.isEmpty()) {
            return objectMapper.convertValue(List.of(), typeReference);
        }
        return objectMapper.convertValue(value, typeReference);
    }
}
