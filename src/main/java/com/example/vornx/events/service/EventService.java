package com.example.vornx.events.service;

import com.example.vornx.access.entity.Site;
import com.example.vornx.access.service.AccessPointService;
import com.example.vornx.access.service.SiteService;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.events.dto.EventRequest;
import com.example.vornx.events.dto.EventResponse;
import com.example.vornx.events.entity.AgendaItem;
import com.example.vornx.events.entity.Event;
import com.example.vornx.events.enums.EventStatus;
import com.example.vornx.events.repository.EventRepository;
import com.example.vornx.events.repository.EventTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventTicketRepository ticketRepository;
    private final SiteService siteService;
    private final AccessPointService accessPointService;
    private final TicketService ticketService;
    private final Clock clock;

    // =============== Partner ===============

    public List<EventResponse> listForPartner(Long partnerId) {
        Instant now = Instant.now(clock);
        return eventRepository.findByPartner_IdOrderByStartsAtDesc(partnerId).stream()
                .map(e -> toResponse(e, now))
                .toList();
    }

    public EventResponse getForPartner(Long partnerId, Long eventId) {
        return toResponse(getOwned(partnerId, eventId), Instant.now(clock));
    }

    @Transactional
    public EventResponse create(Long partnerId, EventRequest req) {
        Site site = siteService.getOwnedSite(partnerId, req.siteId());
        Event event = new Event(site.getPartner(), site, req.title().trim());
        apply(event, site, req);
        return toResponse(eventRepository.save(event), Instant.now(clock));
    }

    @Transactional
    public EventResponse update(Long partnerId, Long eventId, EventRequest req) {
        Event event = eventRepository.lockByIdAndPartnerId(eventId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("EVENT_CANCELLED", "A cancelled event cannot be edited");
        }
        long sold = ticketRepository.countByEvent_IdAndStatusIn(eventId, TicketService.ACTIVE);
        if (req.capacity() != null && req.capacity() < sold) {
            throw new BusinessException("CAPACITY_TOO_LOW", "Capacity cannot be lower than the " + sold + " tickets already taken");
        }
        Site site = siteService.getOwnedSite(partnerId, req.siteId());
        event.setSite(site);
        event.setTitle(req.title().trim());
        apply(event, site, req);
        ticketService.syncPermitTimes(event); // new times -> confirmed tickets' permits follow
        return toResponse(event, Instant.now(clock));
    }

    @Transactional
    public EventResponse publish(Long partnerId, Long eventId) {
        Event event = getOwned(partnerId, eventId);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BusinessException("NOT_DRAFT", "Only a draft event can be published");
        }
        event.setStatus(EventStatus.PUBLISHED);
        return toResponse(event, Instant.now(clock));
    }

    /** Cancels the event: every ticket is refunded and its permit revoked. */
    @Transactional
    public EventResponse cancel(Long partnerId, Long eventId) {
        Event event = eventRepository.lockByIdAndPartnerId(eventId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        Instant now = Instant.now(clock);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("EVENT_CANCELLED", "This event is already cancelled");
        }
        if (!event.getStartsAt().isAfter(now)) {
            throw new BusinessException("EVENT_STARTED", "An event that has started cannot be cancelled");
        }
        event.setStatus(EventStatus.CANCELLED);
        ticketService.cancelAllForEvent(event);
        return toResponse(event, now);
    }

    // =============== Customer catalog ===============

    public PageResponse<EventResponse> catalog(Pageable pageable) {
        Instant now = Instant.now(clock);
        return PageResponse.from(eventRepository.findByStatusAndEndsAtAfter(EventStatus.PUBLISHED, now, pageable)
                .map(e -> toResponse(e, now)));
    }

    public EventResponse getPublished(Long eventId) {
        Event event = eventRepository.findByIdAndStatus(eventId, EventStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        return toResponse(event, Instant.now(clock));
    }

    // =============== helpers ===============

    private void apply(Event event, Site site, EventRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new BusinessException("INVALID_PERIOD", "endsAt must be after startsAt");
        }
        if (req.doorsOpenAt() != null && req.doorsOpenAt().isAfter(req.startsAt())) {
            throw new BusinessException("INVALID_PERIOD", "Doors must open before the event starts");
        }
        event.setDescription(blankToNull(req.description()));
        event.setCategory(blankToNull(req.category()));
        event.setStartsAt(req.startsAt());
        event.setEndsAt(req.endsAt());
        event.setDoorsOpenAt(req.doorsOpenAt());
        event.setFee(req.fee() != null ? req.fee() : BigDecimal.ZERO);
        event.setCapacity(req.capacity());
        event.setRequiresApproval(Boolean.TRUE.equals(req.requiresApproval()));
        event.setDressCode(blankToNull(req.dressCode()));
        event.setContactPhone(blankToNull(req.contactPhone()));
        event.getAgenda().clear();
        if (req.agenda() != null) {
            req.agenda().forEach(a -> event.getAgenda().add(new AgendaItem(a.time(), a.title().trim())));
        }
        event.getAccessPoints().clear();
        event.getAccessPoints().addAll(accessPointService.findInSite(site, req.accessPointIds()));
    }

    private EventResponse toResponse(Event e, Instant now) {
        long active = e.getCapacity() != null
                ? ticketRepository.countByEvent_IdAndStatusIn(e.getId(), TicketService.ACTIVE)
                : 0;
        return EventResponse.from(e, active, now);
    }

    private Event getOwned(Long partnerId, Long eventId) {
        return eventRepository.findByIdAndPartner_Id(eventId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
