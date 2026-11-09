package com.example.vornx.support.repository;

import com.example.vornx.support.entity.SupportTicket;
import com.example.vornx.support.enums.TicketPriority;
import com.example.vornx.support.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    @EntityGraph(attributePaths = {"partner", "assignedAdmin"})
    Page<SupportTicket> findByPartner_Id(Long partnerId, Pageable pageable);

    @EntityGraph(attributePaths = {"partner", "assignedAdmin", "messages"})
    Optional<SupportTicket> findWithMessagesByIdAndPartner_Id(Long id, Long partnerId);

    @EntityGraph(attributePaths = {"partner", "assignedAdmin", "messages"})
    Optional<SupportTicket> findWithMessagesById(Long id);

    /** Admin inbox: every filter optional. */
    @EntityGraph(attributePaths = {"partner", "assignedAdmin"})
    @Query("""
            SELECT t FROM SupportTicket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:partnerId IS NULL OR t.partner.id = :partnerId)
              AND (:assignedAdminId IS NULL OR t.assignedAdmin.id = :assignedAdminId)
            """)
    Page<SupportTicket> search(@Param("status") TicketStatus status,
                               @Param("priority") TicketPriority priority,
                               @Param("partnerId") Long partnerId,
                               @Param("assignedAdminId") Long assignedAdminId,
                               Pageable pageable);

    long countByStatusNot(TicketStatus status);

    boolean existsByNumber(String number);
}
