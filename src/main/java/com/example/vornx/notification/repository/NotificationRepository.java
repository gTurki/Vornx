package com.example.vornx.notification.repository;

import com.example.vornx.notification.entity.Notification;
import com.example.vornx.notification.enums.RecipientType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * "recipientId IS NULL" needs special care in SQL ("= NULL" is never true),
 * so these queries handle the ADMINS inbox (recipient_id NULL) explicitly.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    String MINE = """
            n.recipientType = :type
            AND ((:recipientId IS NULL AND n.recipientId IS NULL) OR n.recipientId = :recipientId)
            """;

    @Query("SELECT n FROM Notification n WHERE " + MINE + " AND (:unreadOnly = false OR n.readAt IS NULL)")
    Page<Notification> findMine(@Param("type") RecipientType type, @Param("recipientId") Long recipientId,
                                @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE " + MINE + " AND n.readAt IS NULL")
    long countUnread(@Param("type") RecipientType type, @Param("recipientId") Long recipientId);

    @Query("SELECT n FROM Notification n WHERE n.id = :id AND " + MINE)
    Optional<Notification> findMineById(@Param("id") Long id, @Param("type") RecipientType type,
                                        @Param("recipientId") Long recipientId);

    /** One UPDATE statement instead of loading every notification. */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE " + MINE + " AND n.readAt IS NULL")
    int markAllRead(@Param("type") RecipientType type, @Param("recipientId") Long recipientId,
                    @Param("now") Instant now);
}
