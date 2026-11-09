package com.example.vornx.events.enums;

import java.time.Duration;

/** "تذكير بالفعالية" - when to notify the customer before the event starts. */
public enum ReminderOption {
    ONE_HOUR(Duration.ofHours(1)),
    ONE_DAY(Duration.ofDays(1)),
    ONE_WEEK(Duration.ofDays(7));

    private final Duration before;

    ReminderOption(Duration before) {
        this.before = before;
    }

    public Duration before() {
        return before;
    }
}
