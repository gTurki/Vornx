package com.example.vornx.support.dto;

import com.example.vornx.support.enums.TicketCategory;
import com.example.vornx.support.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "طلب دعم جديد" */
public record OpenTicketRequest(
        @NotNull TicketCategory category,
        @NotBlank @Size(max = 200) String subject,
        TicketPriority priority, // default MEDIUM
        @NotBlank @Size(max = 5000) String message
) {
}
