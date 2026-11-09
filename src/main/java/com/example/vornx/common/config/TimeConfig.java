package com.example.vornx.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Time in one place.
 * - Clock: inject it instead of calling Instant.now() directly, so tests can "freeze" time.
 * - ZoneId: the business time zone. "Allowed 08:00-17:00" means Riyadh time, not UTC.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId businessZone(@Value("${vornx.time-zone:Asia/Riyadh}") String zone) {
        return ZoneId.of(zone);
    }
}
