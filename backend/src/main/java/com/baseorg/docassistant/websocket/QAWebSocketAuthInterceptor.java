package com.baseorg.docassistant.websocket;

import com.baseorg.docassistant.service.qa.QAWebSocketTicketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class QAWebSocketAuthInterceptor implements HandshakeInterceptor {

    private final QAWebSocketTicketService ticketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String ticket = httpRequest.getParameter("ticket");
        if (ticket == null || ticket.isBlank()) {
            return false;
        }

        QAWebSocketTicketService.TicketContext context = ticketService.consume(ticket);
        if (context == null) {
            return false;
        }
        attributes.put("userId", context.userId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
