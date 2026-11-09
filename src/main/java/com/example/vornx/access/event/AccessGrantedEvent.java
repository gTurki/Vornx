package com.example.vornx.access.event;

import java.time.Instant;

/**
 * Published by the access engine every time a door is ALLOWED.
 * The events module listens to it to check guests in, without the access module knowing events exist.
 */
public record AccessGrantedEvent(Long decisionId, Long permitId, Long customerId, Instant occurredAt) {
}
