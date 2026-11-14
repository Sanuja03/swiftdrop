package com.swiftdrop.backend.modules.courier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_proofs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryProof {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(name = "photo_object_key", length = 500)
    private String photoObjectKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "previous_proof_id")
    private UUID previousProofId;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    public DeliveryProof(
            UUID parcelId,
            UUID courierId,
            String recipientName,
            String photoObjectKey,
            Instant createdAt,
            UUID previousProofId,
            String changeReason) {

        this.parcelId = parcelId;
        this.courierId = courierId;
        this.recipientName = recipientName;
        this.photoObjectKey = photoObjectKey;
        this.createdAt = createdAt;
        this.previousProofId = previousProofId;
        this.changeReason = changeReason;
    }
}