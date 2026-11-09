package com.example.vornx.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Every 5 seconds: send up to 20 due emails. A failure is retried later (see OutboxEmail.BACKOFF);
 * one bad email never blocks the others.
 *
 * With several app instances, add a lock (ShedLock, or SELECT ... FOR UPDATE SKIP LOCKED on MariaDB 10.6+)
 * so two instances never send the same email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOutboxJob {

    private static final int BATCH = 20;

    private final OutboxEmailRepository outboxRepository;
    private final EmailSender sender;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${vornx.jobs.email-outbox.delay:PT5S}", initialDelayString = "PT10S")
    @Transactional
    public void sendDue() {
        for (OutboxEmail email : outboxRepository.findDue(OutboxEmail.Status.PENDING, Instant.now(clock),
                PageRequest.of(0, BATCH))) {
            try {
                sender.send(email);
                email.markSent(Instant.now(clock));
            } catch (Exception e) {
                email.markFailedAttempt(e.getClass().getSimpleName() + ": " + e.getMessage(), Instant.now(clock));
                log.warn("Email {} to {} failed (attempt {}): {}", email.getId(), email.getToAddress(),
                        email.getAttempts(), e.getMessage());
            }
        }
    }
}
