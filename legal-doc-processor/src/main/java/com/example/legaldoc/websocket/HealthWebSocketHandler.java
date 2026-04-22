package com.example.legaldoc.websocket;

import com.example.legaldoc.health.HealthCheckService;
import com.example.legaldoc.model.ServiceHealthStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class HealthWebSocketHandler extends TextWebSocketHandler {

    private final HealthCheckService healthCheckService;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        sessions.add(session);
        log.info("WebSocket connected: {} (total: {})", session.getId(), sessions.size());
        sendHealth(session, healthCheckService.checkAll());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {} (remaining: {})", session.getId(), sessions.size());
    }

    @Scheduled(fixedDelay = 5000)
    public void broadcastHealth() {
        if (sessions.isEmpty()) return;

        ServiceHealthStatus status = healthCheckService.checkAll();
        for (WebSocketSession session : sessions) {
            try {
                sendHealth(session, status);
            } catch (IOException e) {
                log.warn("Failed to send health to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private void sendHealth(WebSocketSession session, ServiceHealthStatus status) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(status);
            session.sendMessage(new TextMessage(json));
        }
    }
}
