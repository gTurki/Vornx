package com.example.vornx.partner.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.partner.enums.PartnerStatus;
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
import lombok.Setter;

/** An organization that offers its services through VORNX (a hotel group, a club, a company...). */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "partners")
public class Partner extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Sector sector;

    @Column(name = "cr_number", length = 30)
    private String crNumber;

    @Column(length = 20)
    private String phone;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** LAZY = the admin is only loaded from the DB if we actually use it. Always use LAZY on @ManyToOne. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_manager_id")
    private AdminUser accountManager;

    public Partner(String name, Sector sector) {
        this.name = name;
        this.sector = sector;
        this.status = PartnerStatus.ONBOARDING;
    }
}
