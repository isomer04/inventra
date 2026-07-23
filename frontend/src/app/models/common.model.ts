export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** A single field-level validation failure, as emitted by the backend's ApiError. */
export interface FieldViolation {
  field: string;
  message: string;
}

/**
 * Mirrors the backend `ApiError` record exactly.
 *
 * <p>This previously declared `validationErrors?: Record<string, string>`, which the
 * backend has never sent — it returns `violations: [{field, message}]`. The shapes never
 * matched, so the interceptor's validation branch was dead code and every 400 collapsed
 * to the generic top-level "Validation failed" with no indication of which field was bad.
 *
 * <p>`violations` is absent (not empty) on non-validation errors, matching the backend's
 * `@JsonInclude(NON_NULL)`. There is no `path` field on the backend response.
 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  violations?: FieldViolation[];
}
