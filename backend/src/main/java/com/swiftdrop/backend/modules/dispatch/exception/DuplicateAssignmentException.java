package com.swiftdrop.backend.modules.dispatch.exception;

import java.util.UUID;

public class DuplicateAssignmentException extends RuntimeException {

    public DuplicateAssignmentException(UUID parcelId) {
        super("Parcel %s already has an active courier assignment".formatted(parcelId));
    }

    public DuplicateAssignmentException(String message) {
        super(message);
    }
}
