package com.inventra.api.exception;

/**
 * Thrown when a request is semantically invalid but passes bean validation.
 *
 * <p>Use this instead of {@link IllegalArgumentException} for user-facing
 * validation errors. The message is guaranteed to be safe for inclusion in
 * API responses — it must be written by the developer, not derived from
 * third-party exception messages.
 *
 * <p>Prevents raw IllegalArgumentException messages
 * (which may contain internal implementation details) from being echoed to clients.
 *
 * <p>Usage:
 * <pre>
 *     throw new InvalidRequestException("groupBy must be 'type', 'date', or 'date_type'");
 * </pre>
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
