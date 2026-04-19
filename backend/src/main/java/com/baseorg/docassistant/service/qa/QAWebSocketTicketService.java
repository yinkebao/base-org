package com.baseorg.docassistant.service.qa;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket ticket 管理
 */
@Service
public class QAWebSocketTicketService {

    private final Map<String, TicketContext> tickets = new ConcurrentHashMap<>();

    public TicketContext issue(Long userId) {
        cleanupExpired();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        TicketContext context = new TicketContext(ticket, userId, LocalDateTime.now().plusSeconds(60));
        tickets.put(ticket, context);
        return context;
    }

    public TicketContext consume(String ticket) {
        cleanupExpired();
        TicketContext context = tickets.remove(ticket);
        if (context == null || context.expiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return context;
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record TicketContext(String ticket, Long userId, LocalDateTime expiresAt) {
    }
}
