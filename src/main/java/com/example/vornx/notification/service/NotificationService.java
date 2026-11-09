package com.example.vornx.notification.service;

import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.notification.dto.NotificationResponse;
import com.example.vornx.notification.entity.Notification;
import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.enums.RecipientType;
import com.example.vornx.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sending: other modules call notifyCustomer / notifyPartner / notifyAdmins INSIDE their own transaction,
 * so a notification exists only if the action really happened (if it is rolled back, so is the notification).
 *
 * Later, the same place can also send push notifications / emails / SMS.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NotificationRepository notificationRepository;
    private final Clock clock;
    private final ZoneId businessZone;

    // =============== Sending ===============

    /** A reference to what the notification is about (opened when tapped). */
    public record Ref(String type, Long id) {
        public static Ref of(String type, Long id) {
            return new Ref(type, id);
        }
    }

    public void notifyCustomer(Long customerId, NotificationType type, Map<String, ?> params, Ref ref) {
        send(RecipientType.CUSTOMER, customerId, type, params, ref);
    }

    public void notifyPartner(Long partnerId, NotificationType type, Map<String, ?> params, Ref ref) {
        send(RecipientType.PARTNER, partnerId, type, params, ref);
    }

    public void notifyAdmins(NotificationType type, Map<String, ?> params, Ref ref) {
        send(RecipientType.ADMINS, null, type, params, ref);
    }

    private void send(RecipientType recipientType, Long recipientId, NotificationType type, Map<String, ?> params,
                      Ref ref) {
        Map<String, String> text = new LinkedHashMap<>();
        params.forEach((k, v) -> text.put(k, format(v)));
        notificationRepository.save(new Notification(recipientType, recipientId, type, type.render(text),
                ref != null ? ref.type() : null, ref != null ? ref.id() : null));
    }

    /** Dates in Riyadh time, money without scientific notation. */
    private String format(Object value) {
        if (value instanceof Instant i) {
            return DATE_TIME.format(i.atZone(businessZone));
        }
        if (value instanceof BigDecimal b) {
            return b.setScale(2, RoundingMode.HALF_UP).toPlainString(); // always "300.00", never "300" or "3E+2"
        }
        return value != null ? value.toString() : "";
    }

    // =============== Reading (the bell icon) ===============

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(RecipientType type, Long recipientId, boolean unreadOnly,
                                                   Pageable pageable) {
        return PageResponse.from(notificationRepository.findMine(type, recipientId, unreadOnly, pageable)
                .map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public long unreadCount(RecipientType type, Long recipientId) {
        return notificationRepository.countUnread(type, recipientId);
    }

    public NotificationResponse markRead(RecipientType type, Long recipientId, Long notificationId) {
        Notification n = notificationRepository.findMineById(notificationId, type, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        n.markRead(Instant.now(clock));
        return NotificationResponse.from(n);
    }

    public int markAllRead(RecipientType type, Long recipientId) {
        return notificationRepository.markAllRead(type, recipientId, Instant.now(clock));
    }
}
