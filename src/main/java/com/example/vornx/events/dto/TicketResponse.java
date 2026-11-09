package com.example.vornx.events.dto;

import com.example.vornx.events.entity.Event;
import com.example.vornx.events.entity.EventTicket;
import com.example.vornx.events.enums.AttendeeRole;
import com.example.vornx.events.enums.EventTicketStatus;
import com.example.vornx.events.enums.ReminderOption;

import java.time.Instant;

/** "تذكرتك": what the customer shows at the door (QR from verificationCode), or a line in the partner's guest list. */
public record TicketResponse(
        Long id,
        String ticketNumber,
        Long eventId,
        String eventTitle,
        String venue,
        Instant startsAt,
        Instant endsAt,
        boolean upcoming,
        AttendeeRole role,
        String passType,
        String seat,
        EventTicketStatus status,
        ReminderOption reminder,
        String permitNumber,
        String verificationCode,
        String rejectionReason,
        Instant checkedInAt,
        String holderVornxId,
        String holderName
) {
    public static TicketResponse from(EventTicket t, Instant now) {
        Event e = t.getEvent();
        return new TicketResponse(t.getId(), t.getTicketNumber(), e.getId(), e.getTitle(), e.getSite().getName(),
                e.getStartsAt(), e.getEndsAt(), e.isUpcoming(now), t.getRole(), t.getPassType(), t.getSeat(),
                t.getStatus(), t.getReminder(),
                t.getPermit() != null ? t.getPermit().getPermitNumber() : null,
                t.getPermit() != null ? t.getPermit().getVerificationCode() : null,
                t.getRejectionReason(), t.getCheckedInAt(),
                t.getCustomer().getVornxId(), t.getCustomer().getFirstName() + " " + t.getCustomer().getLastName());
    }
}
