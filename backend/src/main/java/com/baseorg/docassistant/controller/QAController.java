package com.baseorg.docassistant.controller;

import com.baseorg.docassistant.dto.ApiResponse;
import com.baseorg.docassistant.dto.qa.QAMessageResponse;
import com.baseorg.docassistant.dto.qa.QARequest;
import com.baseorg.docassistant.dto.qa.QAResponse;
import com.baseorg.docassistant.dto.qa.QASessionResponse;
import com.baseorg.docassistant.dto.qa.QAWebSocketTicketResponse;
import com.baseorg.docassistant.service.QAService;
import com.baseorg.docassistant.service.qa.ConversationService;
import com.baseorg.docassistant.service.qa.QAWebSocketTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 问答控制器
 */
@Tag(name = "问答检索", description = "基于 RAG 的智能问答接口")
@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
public class QAController {

    private final QAService qaService;
    private final ConversationService conversationService;
    private final QAWebSocketTicketService ticketService;

    /**
     * 执行问答
     */
    @Operation(summary = "智能问答", description = "基于企业知识库的 RAG 检索问答")
    @PostMapping
    public ApiResponse<QAResponse> ask(
            @Valid @RequestBody QARequest request,
            @AuthenticationPrincipal Long userId) {

        QAResponse response = qaService.ask(request, userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "创建 QA 会话")
    @PostMapping("/sessions")
    public ApiResponse<QASessionResponse> createSession(@AuthenticationPrincipal Long userId) {
        var session = conversationService.createSession(userId);
        return ApiResponse.success(QASessionResponse.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .lastMessageAt(session.getLastMessageAt())
                .build());
    }

    @Operation(summary = "查询 QA 会话列表")
    @GetMapping("/sessions")
    public ApiResponse<List<QASessionResponse>> listSessions(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(conversationService.listSessions(userId));
    }

    @Operation(summary = "查询 QA 会话消息")
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<QAMessageResponse>> listMessages(@AuthenticationPrincipal Long userId,
                                                             @org.springframework.web.bind.annotation.PathVariable Long sessionId) {
        return ApiResponse.success(conversationService.listMessages(userId, sessionId));
    }

    @Operation(summary = "签发 QA WebSocket ticket")
    @PostMapping("/ws-ticket")
    public ApiResponse<QAWebSocketTicketResponse> issueTicket(@AuthenticationPrincipal Long userId) {
        var ticket = ticketService.issue(userId);
        return ApiResponse.success(QAWebSocketTicketResponse.builder()
                .ticket(ticket.ticket())
                .expiresAt(ticket.expiresAt())
                .build());
    }
}
