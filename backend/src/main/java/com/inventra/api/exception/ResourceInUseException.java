package com.inventra.api.exception;

/**
 * Thrown when a resource cannot be deleted because other resources depend on it.
 *
 * <p>Maps to HTTP 409 CONFLICT. Prefer this over {@link DuplicateResourceException}
 * for referential-integrity guards (e.g. "category has children", "customer has orders")
 * — those are dependency conflicts, not duplicate-resource conflicts.
 *
 * <p>Replaces the semantically wrong use of
 * {@code DuplicateResourceException} in {@code CategoryService.delete()} with this class.
 */
public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(String message) {
        super(message);
    }
}
