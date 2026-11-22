package com.swiftdrop.backend.modules.receipt.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A payment receipt for one parcel. Issued once and never changed afterwards.
 */
@Entity
@Table(name = "receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receipt {

    public static final String CURRENCY = "LKR";
    private static final String NUMBER_PREFIX = "RCT-";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false, unique = true, updatable = false)
    private Parcel parcel;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 40, updatable = false)
    private String receiptNumber;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    private Receipt(Parcel parcel, Instant issuedAt) {
        this.parcel = parcel;
        this.receiptNumber = NUMBER_PREFIX + parcel.getTrackingNumber();
        this.amount = parcel.getFee();
        this.currency = CURRENCY;
        this.issuedAt = issuedAt;
    }

    public static Receipt issueFor(Parcel parcel, Instant issuedAt) {
        return new Receipt(parcel, issuedAt);
    }
}
