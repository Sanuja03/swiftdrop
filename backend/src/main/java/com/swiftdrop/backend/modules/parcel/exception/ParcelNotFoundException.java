package com.swiftdrop.backend.modules.parcel.exception;

/**
 * Thrown when a parcel cannot be found by its id or tracking number.
 */
public class ParcelNotFoundException extends RuntimeException {

    public ParcelNotFoundException(Object identifier) {
        super("Parcel %s was not found".formatted(identifier));
    }
}
