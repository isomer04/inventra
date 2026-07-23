package com.inventra.api.audit;

import com.inventra.api.entity.AuditLog;
import com.inventra.api.repository.AuditLogRepository;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends immutable audit records for security-relevant state-changing operations.
 *
 * <p>Centralises audit logging so that
 * individual services do not need to know about the audit table.
 *
 * <p><b>Transaction propagation:</b> {@code REQUIRES_NEW} ensures that an audit
 * record is persisted even if the outer transaction is rolled back (e.g. the
 * business transaction fails after a partial write — the attempt is still recorded).
 *
 * <p><b>PII / secrets policy:</b> never log email addresses, passwords, tokens,
 * or other PII in {@code oldValue}/{@code newValue}. Use role names, status enums,
 * and entity IDs only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Record an audit event. Runs in its own transaction so the record persists
     * regardless of the outcome of the calling transaction.
     *
     * @param actorId    user ID of the authenticated caller (may be null for system events)
     * @param actorEmail email snapshot of the caller (never logged beyond this table)
     * @param eventType  coarse category, e.g. {@code "USER_CREATED"}, {@code "ORDER_SUBMITTED"}
     * @param entityType entity class name, e.g. {@code "User"}, {@code "Order"}
     * @param entityId   entity primary key
     * @param oldValue   JSON snippet of key previous-state fields (no secrets)
     * @param newValue   JSON snippet of key new-state fields (no secrets)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorId,
                       String actorEmail,
                       String eventType,
                       String entityType,
                       String entityId,
                       String oldValue,
                       String newValue) {
        try {
            String tenantId = TenantContext.isSet() ? TenantContext.getTenantId() : null;
            auditLogRepository.save(AuditLog.builder()
                    .tenantId(tenantId)
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .eventType(eventType)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .build());
        } catch (Exception ex) {
            // Audit failures must never break the business flow.
            // Log the failure loudly so it surfaces in monitoring.
            log.error("AUDIT_FAILURE: failed to persist audit record for event={} entity={}/{} actor={}",
                    LogSanitizer.sanitize(eventType), LogSanitizer.sanitize(entityType),
                    LogSanitizer.sanitize(entityId), LogSanitizer.sanitize(actorId), ex);
        }
    }
}
