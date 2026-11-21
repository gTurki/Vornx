package com.example.vornx.pass;

import com.example.vornx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A card the customer added to VORNX by scanning it (a barber's loyalty card, a museum membership, a train ticket...).
 * We store what its barcode / QR code contains, so the app can show the same code at the counter.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer_passes")
public class CustomerPass extends BaseEntity {

    /** The barcode kinds the app can scan and draw again. */
    public enum Format { QR_CODE, AZTEC, PDF417, DATA_MATRIX, CODE_128, CODE_39, CODE_93, EAN_13, EAN_8, UPC_A, UPC_E, ITF, CODABAR }

    @Setter(AccessLevel.NONE)
    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 2048)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Format format;

    @Column(name = "shown_number", length = 80)
    private String shownNumber;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "place_name", length = 120)
    private String placeName;

    private Double latitude;

    private Double longitude;

    public CustomerPass(Long customerId, String name, String code, Format format, String shownNumber, String color) {
        this.customerId = customerId;
        this.name = name;
        this.code = code;
        this.format = format;
        this.shownNumber = shownNumber;
        this.color = color;
    }
}
