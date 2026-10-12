package com.praveen.ragassistant.exception;

/**
 * Thrown when an uploaded file is invalid (not a PDF, empty, corrupted, or oversized).
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
