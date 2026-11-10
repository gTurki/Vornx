package com.example.vornx.events.dto;

import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.events.entity.Event;
import com.example.vornx.events.enums.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EventResponse(
        Long id,
        String title,
        String description,
        String category,
        String organizer,
        Long siteId,
        String venue,
        String address,
        Double latitude,
        Double longitude,
        Instant startsAt,
        Instant endsAt,
        Instant doorsOpenAt,
        BigDecimal fee,
        Integer capacity,
        Long spotsLeft,     // null = unlimited
        boolean requiresApproval,
        String dressCode,
        String contactPhone,
        EventStatus status,
        boolean upcoming,
        List<Long> accessPointIds,   // empty = the whole site
        List<EventRequest.AgendaItemDto> agenda
) {
    public static EventResponse from(Event e, long activeTickets, Instant now) {
        List<EventRequest.AgendaItemDto> agenda = e.getAgenda().stream()
                .map(a -> new EventRequest.AgendaItemDto(a.getTime(), a.getTitle()))
                .toList();
        Long left = e.getCapacity() != null ? Math.max(0, e.getCapacity() - activeTickets) : null;
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getCategory(),
                e.getPartner().getName(), e.getSite().getId(), e.getSite().getName(), e.getSite().getAddress(),
                e.getSite().getLatitude(), e.getSite().getLongitude(),
                e.getStartsAt(), e.getEndsAt(), e.getDoorsOpenAt(), e.getFee(), e.getCapacity(), left,
                e.isRequiresApproval(), e.getDressCode(), e.getContactPhone(), e.getStatus(), e.isUpcoming(now),
                e.getAccessPoints().stream().map(AccessPoint::getId).sorted().toList(),
                agenda);
    }
}
