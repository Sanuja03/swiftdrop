package com.swiftdrop.backend.modules.assistant.exception;

/**
 * Thrown when an error occurs communicating with the Generative AI provider.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
