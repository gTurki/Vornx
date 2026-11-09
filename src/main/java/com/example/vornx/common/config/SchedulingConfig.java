package com.example.vornx.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled background jobs (e.g. EventReminderJob). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
