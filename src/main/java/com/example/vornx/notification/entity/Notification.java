package com.example.vornx.notification.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.enums.RecipientType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One in-app notification. Text is stored in both languages; each app shows its own. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 20, updatable = false)
    private RecipientType recipientType;

    /** customers.id or partners.id; null for ADMINS. */
    @Column(name = "recipient_id", updatable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Column(name = "title_ar", nullable = false, length = 200, updatable = false)
    private String titleAr;

    @Column(name = "title_en", nullable = false, length = 200, updatable = false)
    private String titleEn;

    @Column(name = "body_ar", nullable = false, length = 1000, updatable = false)
    private String bodyAr;

    @Column(name = "body_en", nullable = false, length = 1000, updatable = false)
    private String bodyEn;

    @Column(name = "reference_type", length = 40, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(name = "read_at")
    private Instant readAt;

    public Notification(RecipientType recipientType, Long recipientId, NotificationType type,
                        NotificationType.Rendered text, String referenceType, Long referenceId) {
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.type = type;
        this.titleEn = cut(text.titleEn(), 200);
        this.titleAr = cut(text.titleAr(), 200);
        this.bodyEn = cut(text.bodyEn(), 1000);
        this.bodyAr = cut(text.bodyAr(), 1000);
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
