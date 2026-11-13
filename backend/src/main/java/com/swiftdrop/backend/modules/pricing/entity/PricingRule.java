package com.swiftdrop.backend.modules.pricing.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pricing_rules")
@Getter
@NoArgsConstructor
public class PricingRule {
    @Id private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, unique = true, length = 20)
    private ServiceType serviceType;
    @Column(name = "base_fee", nullable = false, precision = 12, scale = 2) private BigDecimal baseFee;
    @Column(name = "per_kg_fee", nullable = false, precision = 12, scale = 2) private BigDecimal perKgFee;
    @Column(name = "inter_zone_fee", nullable = false, precision = 12, scale = 2) private BigDecimal interZoneFee;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
