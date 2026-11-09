package com.example.vornx.support.dto;

import com.example.vornx.common.enums.ActorType;
import com.example.vornx.support.entity.SupportTicket;
import com.example.vornx.support.entity.TicketMessage;
import com.example.vornx.support.enums.TicketCategory;
import com.example.vornx.support.enums.TicketPriority;
import com.example.vornx.support.enums.TicketStatus;

import java.time.Instant;
import java.util.List;

/** messages is null in lists (to keep them light) and filled when opening one ticket. */
public record SupportTicketResponse(
        Long id,
        String number,
        Long partnerId,
        String partnerName,
        TicketCategory category,
        String subject,
        TicketPriority priority,
        TicketStatus status,
        String assignedTo,
        Instant createdAt,
        Instant lastMessageAt,
        List<MessageView> messages
) {
    public record MessageView(ActorType senderType, String senderName, String body, Instant sentAt) {
        static MessageView from(TicketMessage m) {
            return new MessageView(m.getSenderType(), m.getSenderName(), m.getBody(), m.getCreatedAt());
        }
    }

    public static SupportTicketResponse summary(SupportTicket t) {
        return build(t, null);
    }

    public static SupportTicketResponse detail(SupportTicket t) {
        return build(t, t.getMessages().stream().map(MessageView::from).toList());
    }

    private static SupportTicketResponse build(SupportTicket t, List<MessageView> messages) {
        return new SupportTicketResponse(t.getId(), t.getNumber(), t.getPartner().getId(), t.getPartner().getName(),
                t.getCategory(), t.getSubject(), t.getPriority(), t.getStatus(),
                t.getAssignedAdmin() != null ? t.getAssignedAdmin().getFullName() : null,
                t.getCreatedAt(), t.getLastMessageAt(), messages);
    }
}
