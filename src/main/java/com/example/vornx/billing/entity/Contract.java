package com.example.vornx.billing.entity;

import com.example.vornx.billing.enums.ContractStatus;
import com.example.vornx.billing.enums.PlanTier;
import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.partner.entity.Partner;
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

import java.math.BigDecimal;
import java.time.LocalDate;

/** The agreement between VORNX and a partner: what package they have and what they pay each month. */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "contracts")
public class Contract extends BaseEntity {

    /** A contract ending within this many days is "expiring soon". */
    public static final int EXPIRING_SOON_DAYS = 60;

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanTier tier;

    @Column(name = "monthly_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "max_beneficiaries")
    private Integer maxBeneficiaries;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "renewal_count", nullable = false)
    private int renewalCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status;

    @Column(length = 1000)
    private String notes;

    public Contract(String number, Partner partner, String planName, PlanTier tier, BigDecimal monthlyFee,
                    LocalDate startDate, LocalDate endDate) {
        this.number = number;
        this.partner = partner;
        this.planName = planName;
        this.tier = tier;
        this.monthlyFee = monthlyFee;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ContractStatus.ACTIVE;
    }

    public ContractStatus effectiveStatus(LocalDate today) {
        if (status == ContractStatus.TERMINATED) {
            return ContractStatus.TERMINATED;
        }
        if (today.isAfter(endDate)) {
            return ContractStatus.EXPIRED;
        }
        if (!today.plusDays(EXPIRING_SOON_DAYS).isBefore(endDate)) {
            return ContractStatus.EXPIRING_SOON;
        }
        return ContractStatus.ACTIVE;
    }

    /**
     * "تجديد العقد". The new period starts the day after the current end (or today if it already expired),
     * and ends the day before the same date N months later:
     *   ends 2027-02-28 -> new period 2027-03-01 .. 2028-02-29 (leap year: no day lost).
     */
    public void renew(int months, LocalDate today) {
        LocalDate nextStart = endDate.isBefore(today) ? today : endDate.plusDays(1);
        endDate = nextStart.plusMonths(months).minusDays(1);
        renewalCount++;
    }
}
