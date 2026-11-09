package com.example.vornx.events.repository;

import com.example.vornx.events.entity.EventTicket;
import com.example.vornx.events.enums.EventStatus;
import com.example.vornx.events.enums.EventTicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventTicketRepository extends JpaRepository<EventTicket, Long> {

    @EntityGraph(attributePaths = {"event", "event.site", "event.partner", "permit"})
    List<EventTicket> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "permit"})
    List<EventTicket> findByEvent_IdOrderByCreatedAt(Long eventId);

    List<EventTicket> findByEvent_IdAndStatusIn(Long eventId, Collection<EventTicketStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EventTicket t WHERE t.id = :id AND t.event.partner.id = :partnerId")
    Optional<EventTicket> lockForPartner(@Param("id") Long id, @Param("partnerId") Long partnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EventTicket t WHERE t.id = :id AND t.customer.id = :customerId")
    Optional<EventTicket> lockForCustomer(@Param("id") Long id, @Param("customerId") Long customerId);

    Optional<EventTicket> findByPermit_Id(Long permitId);

    /** Confirmed tickets with an unsent reminder, for events starting soon. */
    @EntityGraph(attributePaths = {"event", "event.site", "customer"})
    @Query("""
            SELECT t FROM EventTicket t
            WHERE t.status = :ticketStatus
              AND t.reminder IS NOT NULL
              AND t.reminderSentAt IS NULL
              AND t.event.status = :eventStatus
              AND t.event.startsAt > :now
              AND t.event.startsAt <= :until
            """)
    List<EventTicket> findReminderCandidates(@Param("ticketStatus") EventTicketStatus ticketStatus,
                                             @Param("eventStatus") EventStatus eventStatus,
                                             @Param("now") Instant now,
                                             @Param("until") Instant until);

    long countByEvent_IdAndStatusIn(Long eventId, Collection<EventTicketStatus> statuses);

    boolean existsByEvent_IdAndCustomer_IdAndStatusIn(Long eventId, Long customerId,
                                                     Collection<EventTicketStatus> statuses);

    boolean existsByTicketNumber(String ticketNumber);
}
