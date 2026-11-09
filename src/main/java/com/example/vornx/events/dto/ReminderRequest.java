package com.example.vornx.events.dto;

import com.example.vornx.events.enums.ReminderOption;

/** null reminder = remove it ("أُزيل التذكير"). */
public record ReminderRequest(ReminderOption reminder) {
}
