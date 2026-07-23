package com.inventra.api.security;

import com.inventra.api.repository.RefreshTokenRepository;
import com.inventra.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Periodically purges expired refresh tokens to prevent unbounded table growth.
 * Expired tokens cannot be used regardless (consumeIfActive checks expiresAt),
 * so deletion is safe at any time.
 *
 * <p><b>Async / TenantContext contract:</b> this service intentionally
 * operates across ALL tenants — it is a cross-tenant administrative operation.
 * It does NOT set a TenantContext and MUST NOT do so, because:
 * <ul>
 *   <li>It runs on a scheduler thread that has no HTTP request context.
 *   <li>The DELETE query has no tenant_id predicate — it cleans expired tokens
 *       regardless of tenant, which is correct: an expired token is expired for
 *       every tenant.
 * </ul>
 *
 * <p>Any future scheduled or async method that needs to access tenanted data
 * must follow the contract documented in {@link TenantContext}: receive the
 * tenantId as a parameter, set it explicitly at the start, and clear it in
 * a finally block.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Runs every 6 hours. Deletes all tokens whose expiry has passed.
     * The fixed-rate string uses ISO-8601 duration format.
     *
     * <p>Wrapped in try/catch so that an unexpected DB error
     * (e.g. transient connection loss) does not terminate the Spring scheduler thread.
     * Spring's default single-thread scheduler does not restart a thread that dies with
     * an uncaught exception — all subsequent scheduled tasks would silently stop running.
     */
    @Scheduled(fixedRateString = "PT6H")
    public void purgeExpiredTokens() {
        // Defensive assertion: this method must never run with a tenant context
        // set (that would indicate it was called from a tenant-scoped request
        // thread, which would be a programming error).
        if (TenantContext.isSet()) {
            log.error("purgeExpiredTokens called with TenantContext set — this is a programming error. " +
                      "Tenant: {}. Proceeding anyway as the DELETE is cross-tenant safe, " +
                      "but the caller must not set TenantContext before invoking scheduled tasks.",
                      TenantContext.getTenantId());
        }

        try {
            int deleted = refreshTokenRepository.deleteExpired(Instant.now());
            if (deleted > 0) {
                log.info("Purged {} expired refresh token(s)", deleted);
            }
        } catch (Exception ex) {
            // Log and swallow — must not kill the scheduler thread.
            // The next scheduled execution (in 6 h) will retry.
            log.error("Failed to purge expired refresh tokens — will retry at next scheduled interval", ex);
        }
    }
}
