package com.inventra.api.audit;

import com.inventra.api.repository.AuditLogRepository;
import com.inventra.api.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * <p>Enforces the data retention policy for the {@code audit_log} table.
 *
 * <p>Audit records contain {@code actor_email} (PII under GDPR Art. 4).
 * Retaining PII indefinitely violates GDPR Art. 5(1)(e) (storage limitation).
 * This service purges records older than the configured retention window.
 *
 * <p><b>Default retention: 365 days.</b> Override with the
 * {@code app.audit.retention-days} property. Minimum enforced: 90 days
 * (shorter retention would impair security investigations).
 *
 * <p><b>Two-phase approach:</b>
 * <ol>
 *   <li>Step 1: anonymise {@code actor_email} in records
 *       older than the retention window — preserves the audit trail while
 *       removing PII.
 *   <li>Step 2 (future): hard-delete records older than a longer archive
 *       window (e.g. 7 years) for legal compliance.
 * </ol>
 *
 * <p><b>TenantContext contract:</b> this is a cross-tenant administrative
 * operation. It does NOT set TenantContext and must not do so.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogRetentionService {

    private final EntityManager entityManager;

    /**
     * Number of days to retain PII (actor_email) in audit records.
     * After this window, actor_email is anonymised to "[retained-for-audit]".
     * Minimum: 90 days. Default: 365 days.
     */
    @Value("${app.audit.retention-days:365}")
    private int retentionDays;

    /**
     * Runs daily at 03:00 UTC. Anonymises actor_email in audit records older
     * than the retention window.
     *
     * <p>The {@code zone} attribute is required: without it the cron runs in the server's
     * default timezone, so a non-UTC host silently contradicts this doc — and the run time
     * would shift twice a year under daylight saving.</p>
     *
     * <p>Enforces GDPR Art. 5(1)(e) storage limitation for PII
     * in the audit log.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void anonymiseExpiredAuditPii() {
        if (TenantContext.isSet()) {
            log.error("anonymiseExpiredAuditPii called with TenantContext set — programming error. Proceeding.");
        }

        int effectiveDays = Math.max(retentionDays, 90);
        Instant cutoff = Instant.now().minus(effectiveDays, ChronoUnit.DAYS);

        try {
            Query query = entityManager.createNativeQuery(
                    "UPDATE audit_log SET actor_email = '[retained-for-audit]' " +
                    "WHERE occurred_at < :cutoff AND actor_email IS NOT NULL " +
                    "AND actor_email != '[retained-for-audit]'");
            query.setParameter("cutoff", cutoff);
            int updated = query.executeUpdate();

            if (updated > 0) {
                log.info("Audit PII retention: anonymised actor_email in {} record(s) older than {} days",
                        updated, effectiveDays);
            }
        } catch (Exception ex) {
            log.error("Audit PII retention job failed — will retry tomorrow", ex);
        }
    }
}
