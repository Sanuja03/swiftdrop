package com.swiftdrop.backend.modules.dispatch.entity;

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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks the assignment of a parcel to a courier by a dispatcher.
 * Kept separate from parcel delivery lifecycle to preserve modularity.
 */
@Entity(name = "DispatchCourierAssignment")
@Table(name = "courier_assignments")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CourierAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ColumnDefault("gen_random_uuid()")
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "dispatcher_id")
    private UUID dispatcherId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ASSIGNED'")
    @Column(name = "status", nullable = false, length = 30)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @Column(name = "notes", length = 500)
    private String notes;

    @Builder.Default
    @Column(name = "scheduled_date", nullable = false)
    @ColumnDefault("CURRENT_DATE")
    private LocalDate scheduledDate = LocalDate.now();

    @CreationTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @UpdateTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private long version;

    public void accept() {
        if (status != AssignmentStatus.ASSIGNED) {
            throw new IllegalStateException("Cannot accept assignment in status: " + status);
        }
        this.status = AssignmentStatus.ACCEPTED;
    }

    public void reject(String reason) {
        if (status != AssignmentStatus.ASSIGNED) {
            throw new IllegalStateException("Cannot reject assignment in status: " + status);
        }
        this.status = AssignmentStatus.REJECTED;
        appendNote("Rejection reason", reason);
    }

    public void cancel(String reason) {
        if (!status.isActive()) {
            throw new IllegalStateException("Cannot cancel non-active assignment in status: " + status);
        }
        this.status = AssignmentStatus.CANCELLED;
        appendNote("Cancel reason", reason);
    }

    public void complete() {
        if (status != AssignmentStatus.ACCEPTED) {
            throw new IllegalStateException("Cannot complete assignment in status: " + status);
        }
        this.status = AssignmentStatus.COMPLETED;
    }

    private void appendNote(String prefix, String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        String entry = prefix + ": " + reason.strip();
        String combined = (this.notes != null && !this.notes.isBlank())
                ? this.notes + " | " + entry
                : entry;
        if (combined.length() > 500) {
            combined = combined.substring(0, 500);
        }
        this.notes = combined;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (assignedAt == null) {
            assignedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = AssignmentStatus.ASSIGNED;
        }
        if (scheduledDate == null) {
            scheduledDate = LocalDate.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
