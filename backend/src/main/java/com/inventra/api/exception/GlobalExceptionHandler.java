package com.inventra.api.exception;

import com.inventra.api.exception.ResourceInUseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map((FieldError fe) -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity
                .badRequest()
                .body(ApiError.of(400, "Bad Request", "Validation failed", violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiError.of(400, "Bad Request", "Malformed or unreadable request body"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of(415, "Unsupported Media Type",
                        "Content type '" + ex.getContentType() + "' is not supported. Use application/json."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        // Authentication failures are expected client events. Log the classification
        // without a full stack trace to keep production logs actionable and avoid noise.
        log.warn("Authentication failure ({})", ex.getClass().getSimpleName());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Unauthorized", "Authentication failed"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(
            org.springframework.web.HttpMediaTypeNotAcceptableException ex) {
        // A JSON ApiError cannot satisfy an Accept header that rejected JSON in the
        // first place. Return an empty 406 to avoid a secondary serialization failure.
        log.debug("No acceptable response representation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "Forbidden", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<ApiError> handleResourceInUse(ResourceInUseException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(InvalidQuantityException.class)
    public ResponseEntity<ApiError> handleInvalidQuantity(InvalidQuantityException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(ConcurrentUpdateException.class)
    public ResponseEntity<ApiError> handleConcurrentUpdate(ConcurrentUpdateException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(CustomerHasOrdersException.class)
    public ResponseEntity<ApiError> handleCustomerHasOrders(CustomerHasOrdersException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidOrderTransition(InvalidOrderTransitionException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(OrderNotEditableException.class)
    public ResponseEntity<ApiError> handleOrderNotEditable(OrderNotEditableException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(EmptyOrderException.class)
    public ResponseEntity<ApiError> handleEmptyOrder(EmptyOrderException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    /**
     * Last line of defence for check-then-act races against unique constraints and for
     * foreign-key violations. Services perform friendly pre-checks (e.g. duplicate email),
     * but only the database constraint is a real guarantee — two concurrent requests can
     * both pass the pre-check. Translating to 409 keeps those races off the 500 path.
     *
     * <p>The exception message is never returned: it contains the constraint and table
     * names, which leak schema details to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Classification only — never the exception itself. The driver embeds the offending
        // value in its message ("Duplicate entry 'someone@example.com' for key
        // 'uk_user_email'"), so logging the throwable copied submitted PII into log storage,
        // which the PII policy on AuditService rules out. The cause's type still identifies
        // which class of constraint failed, which is what the log is read for.
        log.warn("Data integrity violation ({})", ex.getMostSpecificCause().getClass().getName());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict",
                        "The request conflicts with existing data. The resource may already exist "
                                + "or may still be referenced by other records."));
    }

    /**
     * Concurrent modification of a {@code @Version}-guarded entity (Order, InventoryItem).
     * Handled centrally so every versioned save gets a 409 without each service needing
     * its own try/catch.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict",
                        "This record was modified by another request. Reload and try again."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        // Log the real message server-side for debugging; return a safe generic message.
        // Raw IllegalArgumentException messages must not reach the client because they
        // may be thrown by third-party libraries with internal implementation details.
        // Use InvalidRequestException for user-facing validation errors instead.
        log.warn("Invalid argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", "Invalid request parameters"));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error", "An unexpected error occurred"));
    }
}
