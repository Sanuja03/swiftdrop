package com.swiftdrop.backend.modules.courier.entity;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "courier_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourierAssignment {

    @Id
    @Column(name = "parcel_id")
    private UUID parcelId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", insertable = false, updatable = false)
    private Parcel parcel;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;
}
