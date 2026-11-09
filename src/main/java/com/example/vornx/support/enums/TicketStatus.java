package com.example.vornx.support.enums;

/** OPEN = waiting for VORNX. IN_PROGRESS = VORNX replied. CLOSED = done (a new partner message re-opens it). */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED
}
