package com.example.vornx.partner.application;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.partner.enums.Sector;
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

/** "انضم كشريك": a company's request to become a VORNX partner, waiting for the VORNX team's decision. */
@Getter
@Entity
@Table(name = "partner_applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerApplication extends BaseEntity {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "cr_number", nullable = false, length = 10)
    private String crNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sector sector;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(name = "contact_email", nullable = false, length = 160)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(length = 2000)
    private String message;

    @Column(nullable = false, length = 5)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "partner_id")
    private Long partnerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private AdminUser reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    public PartnerApplication(String companyName, String crNumber, Sector sector, String city, String contactName,
                              String jobTitle, String contactEmail, String contactPhone, String message, String language,
                              String ipAddress, String userAgent) {
        this.companyName = companyName;
        this.crNumber = crNumber;
        this.sector = sector;
        this.city = city;
        this.contactName = contactName;
        this.jobTitle = jobTitle;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.message = message;
        this.language = language;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
        this.status = Status.PENDING;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public void approve(Long partnerId, AdminUser by, Instant at) {
        this.status = Status.APPROVED;
        this.partnerId = partnerId;
        this.reviewedBy = by;
        this.reviewedAt = at;
    }

    public void reject(String reason, AdminUser by, Instant at) {
        this.status = Status.REJECTED;
        this.rejectReason = reason;
        this.reviewedBy = by;
        this.reviewedAt = at;
    }
}
