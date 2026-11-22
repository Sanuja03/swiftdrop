package com.swiftdrop.backend.modules.dispatch.exception;

import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import lombok.Getter;

@Getter
public class InvalidAssignmentStateException extends RuntimeException {

    private final AssignmentStatus currentStatus;
    private final String attemptedAction;

    public InvalidAssignmentStateException(AssignmentStatus currentStatus, String attemptedAction) {
        super("Cannot %s courier assignment in status: %s".formatted(attemptedAction, currentStatus));
        this.currentStatus = currentStatus;
        this.attemptedAction = attemptedAction;
    }

    public InvalidAssignmentStateException(String message) {
        super(message);
        this.currentStatus = null;
        this.attemptedAction = null;
    }
}
