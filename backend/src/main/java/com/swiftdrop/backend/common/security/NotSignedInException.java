package com.swiftdrop.backend.common.security;

/** The request has no signed-in user, or that user no longer exists. */
public class NotSignedInException extends RuntimeException {
    public NotSignedInException() {
        super("Sign in to continue.");
    }
}
