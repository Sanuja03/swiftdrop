package com.swiftdrop.backend.modules.assistant.exception;

/**
 * Thrown when AI assistant configuration is invalid or missing required credentials.
 */
public class AiConfigurationException extends RuntimeException {

    public AiConfigurationException(String message) {
        super(message);
    }
}
