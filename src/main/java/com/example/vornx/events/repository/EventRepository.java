package com.example.vornx.events.repository;

import com.example.vornx.events.entity.Event;
import com.example.vornx.events.enums.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = {"site", "partner"})
    List<Event> findByPartner_IdOrderByStartsAtDesc(Long partnerId);

    @EntityGraph(attributePaths = {"site", "partner"})
    Optional<Event> findByIdAndPartner_Id(Long id, Long partnerId);

    /** Customer catalog: published events that haven't ended. */
    @EntityGraph(attributePaths = {"site", "partner"})
    Page<Event> findByStatusAndEndsAtAfter(EventStatus status, Instant now, Pageable pageable);

    @EntityGraph(attributePaths = {"site", "partner"})
    Optional<Event> findByIdAndStatus(Long id, EventStatus status);

    /** Locked while registering: two people can't take the last seat at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id AND e.partner.id = :partnerId")
    Optional<Event> lockByIdAndPartnerId(@Param("id") Long id, @Param("partnerId") Long partnerId);
}
