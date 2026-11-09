package com.example.vornx.notification.dto;

import com.example.vornx.notification.entity.Notification;
import com.example.vornx.notification.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String titleAr,
        String titleEn,
        String bodyAr,
        String bodyEn,
        String referenceType,
        Long referenceId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitleAr(), n.getTitleEn(), n.getBodyAr(),
                n.getBodyEn(), n.getReferenceType(), n.getReferenceId(), n.getReadAt() != null, n.getCreatedAt());
    }
}
