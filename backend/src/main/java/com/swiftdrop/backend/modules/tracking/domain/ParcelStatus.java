package com.swiftdrop.backend.modules.tracking.domain;

import java.util.Set;

import com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException;

/**
 * Lifecycle of a parcel, implemented with the State pattern: every status
 * decides for itself which statuses may follow it.
 *
 * <pre>
 * CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
 *    |                                                   \-> FAILED
 *    \-----> CANCELLED (Merchant or Customer can cancel while CREATED)
 * </pre>
 */
public enum ParcelStatus {

    CREATED("Order Created") {
        @Override
        public Set<ParcelStatus> nextStatuses() {
            return Set.of(PICKED_UP, CANCELLED);
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
    },
    CANCELLED("Cancelled") {
        @Override
        public Set<ParcelStatus> nextStatuses() {
            return Set.of();
        }
    };

    private final String label;

    ParcelStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public abstract Set<ParcelStatus> nextStatuses();

    public boolean canTransitionTo(ParcelStatus target) {
        return target != null && nextStatuses().contains(target);
    }

    public boolean isTerminal() {
        return nextStatuses().isEmpty();
    }

    public void assertCanTransitionTo(ParcelStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
    }
}
