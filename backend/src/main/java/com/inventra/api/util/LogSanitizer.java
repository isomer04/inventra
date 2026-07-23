package com.inventra.api.util;

/**
 * Utility for sanitizing values before they are written to log messages.
 *
 * <p>CRLF injection (CWE-117): an attacker who controls a logged value can
 * embed {@code \r\n} sequences to forge additional log lines, potentially
 * hiding malicious activity or injecting false audit entries.
 *
 * <p>Usage: wrap any user-controlled string in a log argument with
 * {@code LogSanitizer.sanitize(value)} before passing it to SLF4J.
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    /**
     * Strips CR ({@code \r}) and LF ({@code \n}) characters from {@code value}.
     * Returns {@code "[null]"} for null inputs so log messages remain readable.
     *
     * @param value the string to sanitize (may be null)
     * @return the sanitized string, never null
     */
    public static String sanitize(String value) {
        if (value == null) {
            return "[null]";
        }
        return value.replace("\r", "").replace("\n", "");
    }

    /**
     * Convenience overload for non-String objects — calls {@code toString()}
     * then sanitizes.
     *
     * @param value the object to sanitize (may be null)
     * @return the sanitized string, never null
     */
    public static String sanitize(Object value) {
        if (value == null) {
            return "[null]";
        }
        return sanitize(value.toString());
    }
}
