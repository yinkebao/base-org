package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.QAMessageResponse;
import com.baseorg.docassistant.dto.qa.QAResponse;
import com.baseorg.docassistant.entity.QAMessage;
import com.baseorg.docassistant.entity.QASession;
import com.baseorg.docassistant.mapper.QAMessageMapper;
import com.baseorg.docassistant.mapper.QASessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private QASessionMapper qaSessionMapper;

    @Mock
    private QAMessageMapper qaMessageMapper;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(qaSessionMapper, qaMessageMapper, new ObjectMapper());
    }

    @Test
    void shouldPersistSourcesAsStructuredJsonbValue() {
        QAMessage assistantMessage = QAMessage.builder()
                .id(10L)
                .sessionId(20L)
                .userId(30L)
                .role(QAMessage.MessageRole.ASSISTANT)
                .status(QAMessage.MessageStatus.PROCESSING)
                .build();

        List<QAResponse.SourceChunk> sources = List.of(
                QAResponse.SourceChunk.builder()
                        .chunkId(101L)
                        .docId(202L)
                        .docTitle("鉴权文档")
                        .content("系统使用 OAuth2 鉴权")
                        .score(0.92)
                        .chunkIndex(0)
                        .metadata(Map.of("section", "认证"))
                        .build()
        );

        conversationService.completeAssistantMessage(
                assistantMessage,
                "这是答案",
                "RAG_SEARCH",
                "系统如何鉴权？",
                sources,
                0.91,
                false,
                null,
                null,
                123L,
                "sha256:test",
                null,
                List.of(),
                List.of(),
                List.of()
        );

        ArgumentCaptor<QAMessage> captor = ArgumentCaptor.forClass(QAMessage.class);
        verify(qaMessageMapper).updateById(captor.capture());

        QAMessage persisted = captor.getValue();
        assertThat(persisted.getSourcesJson()).hasSize(1);
        assertThat(persisted.getSourcesJson().get(0))
                .containsEntry("docTitle", "鉴权文档")
                .containsEntry("chunkId", 101L);
    }

    @Test
    void shouldConvertStructuredSourcesBackToResponseChunks() {
        Long userId = 30L;
        Long sessionId = 20L;

        when(qaSessionMapper.selectOne(any())).thenReturn(QASession.builder()
                .id(sessionId)
                .userId(userId)
                .title("测试会话")
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .build());
        when(qaMessageMapper.findBySessionId(sessionId, userId, 100)).thenReturn(List.of(
                QAMessage.builder()
                        .id(10L)
                        .sessionId(sessionId)
                        .userId(userId)
                        .role(QAMessage.MessageRole.ASSISTANT)
                        .content("这是答案")
                        .sourcesJson(List.of(Map.of(
                                "chunkId", 101L,
                                "docId", 202L,
                                "docTitle", "鉴权文档",
                                "content", "系统使用 OAuth2 鉴权",
                                "score", 0.92,
                                "chunkIndex", 0,
                                "metadata", Map.of("section", "认证")
                        )))
                        .status(QAMessage.MessageStatus.COMPLETED)
                        .createdAt(LocalDateTime.now())
                        .build()
        ));

        List<QAMessageResponse> responses = conversationService.listMessages(userId, sessionId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getSources()).hasSize(1);
        assertThat(responses.get(0).getSources().get(0).getDocTitle()).isEqualTo("鉴权文档");
        assertThat(responses.get(0).getSources().get(0).getChunkId()).isEqualTo(101L);
        verify(qaMessageMapper).findBySessionId(eq(sessionId), eq(userId), eq(100));
    }
}
