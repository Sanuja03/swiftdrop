package com.swiftdrop.backend.modules.tracking.exception;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

/**
 * Thrown when a status change breaks the parcel lifecycle rules.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    private final ParcelStatus from;
    private final ParcelStatus to;

    public InvalidStatusTransitionException(ParcelStatus from, ParcelStatus to) {
        super("A parcel cannot move from %s to %s".formatted(from, to));
        this.from = from;
        this.to = to;
    }

    public ParcelStatus getFrom() {
        return from;
    }

    public ParcelStatus getTo() {
        return to;
    }
}
