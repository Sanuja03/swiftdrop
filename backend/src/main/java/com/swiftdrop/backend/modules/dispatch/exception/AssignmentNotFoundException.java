package com.swiftdrop.backend.modules.dispatch.exception;

import java.util.UUID;

public class AssignmentNotFoundException extends RuntimeException {

    public AssignmentNotFoundException(UUID assignmentId) {
        super("Courier assignment %s was not found".formatted(assignmentId));
    }

    public AssignmentNotFoundException(String message) {
        super(message);
    }
}
