package com.example.vornx.access.entity;

import com.example.vornx.access.enums.AccessMethod;
import com.example.vornx.access.enums.AccessPointStatus;
import com.example.vornx.access.enums.AccessPointType;
import com.example.vornx.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Something inside a site that can be opened: room 1204, the east gate, parking barrier B... */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "access_points")
public class AccessPoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false, updatable = false)
    private Site site;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccessPointType type;

    @Column(length = 20)
    private String floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessPointStatus status;

    /** Reported by the lock/reader itself (later, through the device API). */
    @Column(nullable = false)
    private boolean online;

    /**
     * @ElementCollection = a simple list of values stored in its own table (access_point_methods),
     * no separate entity needed.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "access_point_methods", joinColumns = @JoinColumn(name = "access_point_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private Set<AccessMethod> supportedMethods = new HashSet<>();

    public AccessPoint(Site site, String code, String name, AccessPointType type) {
        this.site = site;
        this.code = code;
        this.name = name;
        this.type = type;
        this.status = AccessPointStatus.AVAILABLE;
        this.online = false;
    }

    public void replaceSupportedMethods(Set<AccessMethod> methods) {
        supportedMethods.clear();
        if (methods != null && !methods.isEmpty()) {
            supportedMethods.addAll(EnumSet.copyOf(methods));
        }
    }
}
