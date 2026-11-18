package com.swiftdrop.backend.modules.tracking.entity;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One entry in a parcel's status timeline. Rows are never updated, only added.
 */
@Entity
@Table(name = "parcel_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParcelStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false, updatable = false)
    private Parcel parcel;

    /** Null for the first entry, when the parcel is created. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, updatable = false)
    private ParcelStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private ParcelStatus toStatus;

    @Column(name = "note", length = 500, updatable = false)
    private String note;

    /** User who made the change, when known. */
    @Column(name = "changed_by", updatable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public ParcelStatusHistory(Parcel parcel, ParcelStatus fromStatus, ParcelStatus toStatus,
                               String note, UUID changedBy, Instant changedAt) {
        this.parcel = parcel;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }
}
