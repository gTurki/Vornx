package com.example.vornx.security.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Every night at 03:30 Riyadh time: delete sessions (and their tokens) that ended over 30 days ago. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private final AuthSessionRepository sessionRepository;
    private final Clock clock;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Riyadh")
    @Transactional
    public void deleteOldSessions() {
        int deleted = sessionRepository.deleteEndedBefore(Instant.now(clock).minus(Duration.ofDays(30)));
        if (deleted > 0) {
            log.info("Deleted {} old session(s)", deleted);
        }
    }
}
