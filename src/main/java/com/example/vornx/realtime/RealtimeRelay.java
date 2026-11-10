package com.example.vornx.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends a RealtimePush to the browsers AFTER the transaction that created it commits.
 * (fallbackExecution: also works when published outside a transaction.)
 * A failed push never breaks the business action: the data is saved, and the app also refreshes on its own.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeRelay {

    private final RealtimeHub hub;
    private final ObjectMapper json;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(RealtimePush push) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", push.type());
            msg.put("data", push.data());
            hub.send(push.channel(), json.writeValueAsString(msg));
        } catch (RuntimeException e) {
            log.warn("Realtime push {} to {} failed: {}", push.type(), push.channel(), e.getMessage());
        }
    }
}
