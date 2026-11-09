package com.example.vornx.email;

import com.example.vornx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/** One email waiting to be sent (or already sent / failed). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "email_outbox")
public class OutboxEmail extends BaseEntity {

    public enum Status { PENDING, SENT, FAILED }

    /** Wait before each retry: 1 min, 5 min, 30 min, 2 h. After that it is FAILED. */
    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2)};

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private EmailType type;

    @Column(name = "to_address", nullable = false, length = 120, updatable = false)
    private String toAddress;

    @Column(nullable = false, length = 200, updatable = false)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "MEDIUMTEXT", updatable = false)
    private String htmlBody;

    @Column(name = "text_body", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String textBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public OutboxEmail(EmailType type, String toAddress, String subject, String htmlBody, String textBody, Instant now) {
        this.type = type;
        this.toAddress = toAddress;
        this.subject = subject.length() > 200 ? subject.substring(0, 199) + "…" : subject;
        this.htmlBody = htmlBody;
        this.textBody = textBody;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
    }

    public void markSent(Instant now) {
        status = Status.SENT;
        sentAt = now;
        attempts++;
        lastError = null;
    }

    public void markFailedAttempt(String error, Instant now) {
        lastError = error == null ? "unknown error" : error.length() > 500 ? error.substring(0, 500) : error;
        if (attempts < BACKOFF.length) {
            nextAttemptAt = now.plus(BACKOFF[attempts]);
        } else {
            status = Status.FAILED; // stop retrying; visible in the table for someone to look at
        }
        attempts++;
    }
}
