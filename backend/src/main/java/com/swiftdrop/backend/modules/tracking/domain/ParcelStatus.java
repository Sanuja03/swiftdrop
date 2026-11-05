package com.swiftdrop.backend.modules.tracking.domain;

import java.util.Set;

import com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException;

import lombok.Getter;

/**
 * Lifecycle of a parcel, implemented with the State pattern: every status
 * decides for itself which statuses may follow it.
 *
 * <pre>
 * CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
 *                                                       \-> FAILED
 * </pre>
 */
@Getter
public enum ParcelStatus {

    CREATED("Order Created") {
        @Override
        public Set<ParcelStatus> nextStatuses() {
            return Set.of(PICKED_UP);
        }
    },
    PICKED_UP("Picked Up") {
        @Override
        public Set<ParcelStatus> nextStatuses() {
            return Set.of(IN_TRANSIT);
        }
    },
    IN_TRANSIT("In Transit") {
        @Override
        public Set<ParcelStatus> nextStatuses() {
            return Set.of(OUT_FOR_DELIVERY);
        }
    },
    OUT_FOR_DELIVERY("Out for Delivery") {
        @Override
        public Set<ParcelStatus> nextStatuses() {
            return Set.of(DELIVERED, FAILED);
        }
    },
    DELIVERED("Delivered") {
        @Override
        public Set<ParcelStatus> nextStatuses() {
            return Set.of();
        }
    },
    FAILED("Delivery Failed") {
        @Override
        public Set<ParcelStatus> nextStatuses() {
            return Set.of();
        }
    };

    private final String label;

    ParcelStatus(String label) {
        this.label = label;
    }

    /**
     * @return the statuses this status is allowed to move to
     */
    public abstract Set<ParcelStatus> nextStatuses();

    public boolean canTransitionTo(ParcelStatus target) {
        return target != null && nextStatuses().contains(target);
    }

    public boolean isTerminal() {
        return nextStatuses().isEmpty();
    }

    /**
     * @throws InvalidStatusTransitionException if {@code target} may not follow this status
     */
    public void assertCanTransitionTo(ParcelStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
    }
}
