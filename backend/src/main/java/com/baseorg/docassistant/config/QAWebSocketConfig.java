package com.baseorg.docassistant.config;

import com.baseorg.docassistant.websocket.QAWebSocketAuthInterceptor;
import com.baseorg.docassistant.websocket.QAWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class QAWebSocketConfig implements WebSocketConfigurer {

    private final QAWebSocketHandler qaWebSocketHandler;
    private final QAWebSocketAuthInterceptor qaWebSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(qaWebSocketHandler, "/ws/qa")
                .addInterceptors(qaWebSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
