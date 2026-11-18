package com.swiftdrop.backend.modules.dispatch.entity;

/**
 * Status lifecycle of a courier dispatch assignment.
 * Kept strictly independent from ParcelStatus delivery lifecycle.
 */
public enum AssignmentStatus {
    ASSIGNED,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    COMPLETED;

    /**
     * @return true if the courier is actively responsible for delivering this parcel.
     */
    public boolean isActive() {
        return this == ASSIGNED || this == ACCEPTED;
    }
}
