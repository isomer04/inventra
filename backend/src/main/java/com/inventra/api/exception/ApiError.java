package com.inventra.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        List<FieldViolation> violations,
        Instant timestamp
) {
    // Compact constructor: defensively copy the mutable list to prevent
    // EI_EXPOSE_REP2 (storing externally mutable object) and EI_EXPOSE_REP
    // (returning internal mutable representation via the accessor).
    public ApiError {
        violations = violations != null ? List.copyOf(violations) : null;
    }

    public record FieldViolation(String field, String message) {}

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, null, Instant.now());
    }

    public static ApiError of(int status, String error, String message, List<FieldViolation> violations) {
        return new ApiError(
                status,
                error,
                message,
                violations == null ? null : List.copyOf(violations),
                Instant.now()
        );
    }
}
