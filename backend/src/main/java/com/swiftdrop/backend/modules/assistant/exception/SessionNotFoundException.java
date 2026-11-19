package com.swiftdrop.backend.modules.assistant.exception;

/**
 * Thrown when an assistant chat session cannot be found.
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String message) {
        super(message);
    }
}
