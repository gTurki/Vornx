package com.example.vornx.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is connected, and on which channel. In memory: fine for ONE backend instance (our Railway setup).
 * With several instances, a push must reach all of them - then put Redis pub/sub between relay and hub.
 */
@Slf4j
@Component
public class RealtimeHub {

    private final Map<String, Set<WebSocketSession>> byChannel = new ConcurrentHashMap<>();

    public void join(String channel, WebSocketSession session) {
        byChannel.computeIfAbsent(channel, c -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void leave(String channel, WebSocketSession session) {
        byChannel.computeIfPresent(channel, (c, set) -> {
            set.remove(session);
            return set.isEmpty() ? null : set;
        });
    }

    public void send(String channel, String json) {
        Set<WebSocketSession> sessions = byChannel.get(channel);
        if (sessions == null) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(message); // sessions are wrapped to be thread-safe (see RealtimeSocketHandler)
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("Realtime send failed, closing session {}: {}", s.getId(), e.getMessage());
                close(s, CloseStatus.SESSION_NOT_RELIABLE);
            }
        }
    }

    public int connections() {
        return byChannel.values().stream().mapToInt(Set::size).sum();
    }

    static void close(WebSocketSession s, CloseStatus status) {
        try {
            s.close(status);
        } catch (IOException ignored) {
            // already gone
        }
    }
}
