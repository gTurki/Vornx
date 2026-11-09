package com.example.vornx.events.dto;

import com.example.vornx.events.enums.AttendeeRole;
import jakarta.validation.constraints.Size;

/** Partner sets the guest's role, pass and seat ("قاعة A — صف 2"). Send only what changes. */
public record TicketUpdateRequest(
        AttendeeRole role,
        @Size(max = 50) String passType,
        @Size(max = 30) String seat
) {
}
