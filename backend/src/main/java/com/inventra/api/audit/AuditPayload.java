package com.inventra.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the JSON snippets stored in {@code audit_log.old_value} / {@code new_value}.
 *
 * <p>Replaces hand-rolled string concatenation such as
 * {@code "{\"slug\":\"" + value + "\"}"}. Concatenation happens to produce valid JSON only
 * while every interpolated value is constrained (slug regex, enum names); the first
 * unconstrained value containing a quote or backslash silently corrupts the audit row.
 * Jackson escapes properly, so correctness no longer depends on the caller's inputs.
 *
 * <p><b>PII / secrets policy</b> (inherited from {@link AuditService}): pass role names,
 * status enums, and entity IDs only — never passwords, tokens, or email addresses.
 */
@Slf4j
public final class AuditPayload {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuditPayload() {
    }

    /** Single-field payload, e.g. {@code of("status", UserStatus.ACTIVE)}. */
    public static String of(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, stringify(value));
        return write(map);
    }

    /** Two-field payload, e.g. {@code of("role", role, "status", status)}. */
    public static String of(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key1, stringify(value1));
        map.put(key2, stringify(value2));
        return write(map);
    }

    // Enums and IDs are recorded as strings so the audit column has a stable shape
    // regardless of how Jackson would otherwise serialise the argument's type.
    private static Object stringify(Object value) {
        return value == null ? null : value.toString();
    }

    private static String write(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            // Mirrors AuditService's contract: auditing must never break the business flow.
            log.error("AUDIT_FAILURE: could not serialise audit payload keys={}", map.keySet(), ex);
            return null;
        }
    }
}
