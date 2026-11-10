package com.example.vornx.realtime;

import com.example.vornx.security.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket endpoint: wss://<api>/ws
 *
 * Origins: only our own frontends (the same list as CORS). Browsers send the Origin header on a
 * WebSocket handshake, so another website can't open a socket in a visitor's browser.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RealtimeConfig implements WebSocketConfigurer {

    private final RealtimeSocketHandler handler;
    private final CorsProperties cors;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOrigins(cors.allowedOrigins().toArray(String[]::new));
    }
}
