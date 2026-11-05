package com.swiftdrop.backend.modules.parcel.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A parcel booked for delivery.
 *
 * <p>Minimal version holding only what tracking and receipts need. The status can
 * only change through {@link #changeStatus(ParcelStatus)}, which enforces the
 * lifecycle rules in {@link ParcelStatus}.
 */
@Entity
@Table(name = "parcels")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 32)
    private String trackingNumber;

    /** Owner of the parcel; links to the users table once authentication is in place. */
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "sender_name", nullable = false, length = 120)
    private String senderName;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "pickup_city", nullable = false, length = 80)
    private String pickupCity;

    @Column(name = "dropoff_address", nullable = false)
    private String dropoffAddress;

    @Column(name = "dropoff_city", nullable = false, length = 80)
    private String dropoffCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal fee;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ParcelStatus status = ParcelStatus.CREATED;

    @Column(name = "estimated_delivery_at")
    private Instant estimatedDeliveryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: stops two concurrent status updates from overwriting each other. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Moves the parcel to {@code newStatus} if the lifecycle allows it.
     *
     * @return the previous status
     */
    public ParcelStatus changeStatus(ParcelStatus newStatus) {
        ParcelStatus previous = status;
        previous.assertCanTransitionTo(newStatus);
        status = newStatus;
        return previous;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
