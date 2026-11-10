package com.example.vornx.inquiry;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.platform.entity.AdminUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A message from the public website's contact form. */
@Getter
@Entity
@Table(name = "inquiries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseEntity {

    public enum Interest { TECHNICAL_INTEGRATION, ENTERPRISE, PILOT, STRATEGIC, OTHER }

    public enum Status { NEW, HANDLED }

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 160)
    private String organization;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Interest interest;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false, length = 2)
    private String language;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private AdminUser handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    public Inquiry(String name, String email, String organization, String jobTitle, Interest interest, String message,
                   String language, String ipAddress, String userAgent) {
        this.name = name;
        this.email = email;
        this.organization = organization;
        this.jobTitle = jobTitle;
        this.interest = interest;
        this.message = message;
        this.language = language;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
        this.status = Status.NEW;
    }

    public void markHandled(AdminUser admin, Instant now) {
        this.status = Status.HANDLED;
        this.handledBy = admin;
        this.handledAt = now;
    }

    public void reopen() {
        this.status = Status.NEW;
        this.handledBy = null;
        this.handledAt = null;
    }
}
