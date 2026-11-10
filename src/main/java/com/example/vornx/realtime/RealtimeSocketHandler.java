package com.example.vornx.realtime;

import com.example.vornx.security.AuthUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The WebSocket at /ws. The protocol is tiny JSON:
 *
 *   browser -> {"type":"auth","token":"<access token>"}   first message, within 10 s
 *   server  -> {"type":"ready"}
 *   browser -> {"type":"ping"}                             every ~25 s, keeps proxies from closing it
 *   server  -> {"type":"pong"}
 *   server  -> {"type":"notification","data":{...}}       pushes (see RealtimePush)
 *
 * Why the token is in a message and not in the URL: URLs end up in proxy and server logs.
 * Browsers can't set an Authorization header on a WebSocket, so the first message carries it.
 *
 * The connection lives only as long as the access token (15 min). Then we close it with code 4401,
 * and the app refreshes its token and reconnects - so a logged-out or suspended user is cut off too.
 */
@Slf4j
@Component
public class RealtimeSocketHandler extends TextWebSocketHandler {

    static final CloseStatus AUTH_TIMEOUT = new CloseStatus(4400, "auth required");
    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "token expired");
    private static final Duration AUTH_WITHIN = Duration.ofSeconds(10);
    private static final Duration IDLE_LIMIT = Duration.ofSeconds(90); // the app pings every 25 s
    private static final int MAX_MESSAGE_CHARS = 4096;            // clients only send tiny JSON

    private record Conn(WebSocketSession session, Instant openedAt, String channel, Instant expiresAt, Instant lastSeen) {
        Conn seen(Instant now) {
            return new Conn(session, openedAt, channel, expiresAt, now);
        }
    }

    private final Map<String, Conn> conns = new ConcurrentHashMap<>();
    private final RealtimeHub hub;
    private final JwtDecoder jwtDecoder;
    private final ObjectMapper json;
    private final Clock clock;

    public RealtimeSocketHandler(RealtimeHub hub, JwtDecoder jwtDecoder, ObjectMapper json, Clock clock) {
        this.hub = hub;
        this.jwtDecoder = jwtDecoder;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) {
        // Thread-safe wrapper: pushes from many request threads may hit the same socket at once.
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5_000, 64 * 1024);
        conns.put(raw.getId(), new Conn(session, clock.instant(), null, null, clock.instant()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws Exception {
        Conn conn = conns.get(raw.getId());
        if (conn == null) {
            return;
        }
        if (message.getPayloadLength() > MAX_MESSAGE_CHARS) {
            RealtimeHub.close(conn.session(), CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        conn = conn.seen(clock.instant());
        conns.put(raw.getId(), conn);
        JsonNode msg;
        try {
            msg = json.readTree(message.getPayload());
        } catch (RuntimeException e) {
            RealtimeHub.close(conn.session(), CloseStatus.BAD_DATA);
            return;
        }
        String type = msg.path("type").asText("");

        if ("ping".equals(type)) {
            conn.session().sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        } else if ("auth".equals(type) && conn.channel() == null) {
            authenticate(conn, msg.path("token").asText(""));
        } else {
            RealtimeHub.close(conn.session(), CloseStatus.POLICY_VIOLATION);
        }
    }

    private void authenticate(Conn conn, String token) throws Exception {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token); // signature, issuer and expiry, exactly like the REST API
        } catch (JwtException e) {
            RealtimeHub.close(conn.session(), TOKEN_EXPIRED); // the app refreshes and tries again
            return;
        }
        AuthUser user = AuthUser.from(jwt);
        String channel = switch (user.type()) {
            case CUSTOMER -> RealtimePush.customer(user.id());
            case PARTNER_USER -> RealtimePush.partner(user.partnerId());
            case ADMIN -> RealtimePush.ADMINS;
        };
        conns.put(conn.session().getId(), new Conn(conn.session(), conn.openedAt(), channel, jwt.getExpiresAt(), clock.instant()));
        hub.join(channel, conn.session());
        conn.session().sendMessage(new TextMessage("{\"type\":\"ready\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        Conn conn = conns.remove(raw.getId());
        if (conn != null && conn.channel() != null) {
            hub.leave(conn.channel(), conn.session());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession raw, Throwable error) {
        log.debug("Realtime transport error on {}: {}", raw.getId(), error.getMessage());
    }

    /** Close sockets that never logged in, whose token expired, or that went silent (dead connection). */
    @Scheduled(fixedDelay = 5_000)
    public void sweep() {
        Instant now = clock.instant();
        conns.values().forEach(c -> {
            if (c.channel() == null && c.openedAt().plus(AUTH_WITHIN).isBefore(now)) {
                RealtimeHub.close(c.session(), AUTH_TIMEOUT);
            } else if (c.expiresAt() != null && c.expiresAt().isBefore(now)) {
                RealtimeHub.close(c.session(), TOKEN_EXPIRED);
            } else if (c.lastSeen().plus(IDLE_LIMIT).isBefore(now)) {
                RealtimeHub.close(c.session(), CloseStatus.SESSION_NOT_RELIABLE);
            }
        });
    }

    int openConnections() {
        return conns.size();
    }
}
