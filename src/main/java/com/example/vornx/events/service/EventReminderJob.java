package com.example.vornx.events.service;

import com.example.vornx.events.entity.Event;
import com.example.vornx.events.entity.EventTicket;
import com.example.vornx.events.enums.EventStatus;
import com.example.vornx.events.enums.EventTicketStatus;
import com.example.vornx.events.repository.EventTicketRepository;
import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * A background job: every minute, send the event reminders that are due.
 *
 * A ticket with reminder ONE_DAY for an event at 18:00 tomorrow gets its notification at 18:00 today.
 * reminderSentAt makes sure each reminder goes out only once.
 *
 * Note for later: with 2+ app instances, every instance would run this job. Then add a lock
 * (e.g. the ShedLock library) so only one instance runs it at a time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vornx.jobs.event-reminders.enabled", havingValue = "true", matchIfMissing = true)
public class EventReminderJob {

    /** The longest reminder option: only events starting within this window are checked. */
    private static final Duration MAX_WINDOW = Duration.ofDays(7);

    private final EventTicketRepository ticketRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${vornx.jobs.event-reminders.delay:PT1M}", initialDelayString = "PT20S")
    @Transactional
    public void sendDueReminders() {
        Instant now = Instant.now(clock);
        int sent = 0;
        for (EventTicket t : ticketRepository.findReminderCandidates(EventTicketStatus.CONFIRMED, EventStatus.PUBLISHED,
                now, now.plus(MAX_WINDOW))) {
            Event e = t.getEvent();
            Instant sendAt = e.getStartsAt().minus(t.getReminder().before());
            if (!now.isBefore(sendAt)) {
                notificationService.notifyCustomer(t.getCustomer().getId(), NotificationType.EVENT_REMINDER,
                        Map.of("event", e.getTitle(), "startsAt", e.getStartsAt(), "venue", e.getSite().getName()),
                        NotificationService.Ref.of("EVENT_TICKET", t.getId()));
                t.setReminderSentAt(now);
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Sent {} event reminder(s)", sent);
        }
    }
}
