package com.example.vornx.events.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * One line of the event schedule ("6:00 PM - Opening keynote").
 * @Embeddable = a small value object stored inside another table (event_agenda), with no id of its own.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AgendaItem {

    @Column(name = "item_time", nullable = false)
    private LocalTime time;

    @Column(nullable = false, length = 200)
    private String title;
}
