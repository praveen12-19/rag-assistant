package com.praveen.ragassistant.exception;

/**
 * Thrown when a requested document cannot be located by ID or filename.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message) {
        super(message);
    }
}
