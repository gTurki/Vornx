package com.example.vornx.support.dto;

import com.example.vornx.support.enums.TicketPriority;
import com.example.vornx.support.enums.TicketStatus;

/** Send only what changes. assignedAdminId = 0 removes the assignment. */
public record AdminTicketUpdateRequest(
        TicketStatus status,
        TicketPriority priority,
        Long assignedAdminId
) {
}
