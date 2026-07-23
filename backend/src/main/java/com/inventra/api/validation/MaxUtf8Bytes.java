package com.inventra.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bounds a {@link CharSequence} by its UTF-8 <em>byte</em> length rather than its
 * character count.
 *
 * <p>{@code @Size(max = 72)} counts characters, which does not bound bytes: a password
 * of 72 accented or CJK characters encodes to 100+ bytes. BCrypt only considers the
 * first 72 bytes of its input, so anything past that boundary is not part of the
 * credential — two different passwords sharing a 72-byte prefix hash identically.
 * Rejecting oversized input up front keeps the accepted password and the hashed
 * password the same value.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    /** Maximum number of UTF-8 bytes allowed. */
    int value();

    String message() default "must not exceed {value} bytes when UTF-8 encoded";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
