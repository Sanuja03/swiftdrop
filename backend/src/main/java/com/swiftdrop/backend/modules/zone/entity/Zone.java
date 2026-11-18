package com.swiftdrop.backend.modules.zone.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "zones")
@Getter
@NoArgsConstructor
public class Zone {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal surcharge;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() { var now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
