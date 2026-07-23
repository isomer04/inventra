package com.inventra.api.tenant;

import com.inventra.api.entity.Tenant;
import com.inventra.api.entity.TenantStatus;
import com.inventra.api.exception.InvalidRequestException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.repository.CustomerRepository;
import com.inventra.api.repository.RefreshTokenRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.repository.UserRepository;
import com.inventra.api.tenant.dto.TenantResponse;
import com.inventra.api.tenant.dto.UpdateTenantRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public TenantResponse getTenant(String tenantId) {
        return tenantMapper.toResponse(load(tenantId));
    }

    public TenantResponse updateTenant(String tenantId, UpdateTenantRequest req) {
        Tenant tenant = load(tenantId);
        tenant.setName(req.name());
        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    /**
     * Right-to-erasure (GDPR Art. 17) — pseudonymise all PII for this tenant
     * and suspend the account.
     *
     * <p>Provides a self-service erasure path for tenant admins.
     *
     * <p><b>Why pseudonymisation instead of hard delete:</b> the tenant's orders,
     * stock movements, and audit records are linked by FK to users and customers.
     * A hard DELETE would cascade-delete financial records that may be required for
     * legal/tax retention (typically 7 years). Pseudonymisation removes the
     * identifying information while preserving the operational record.
     *
     * <p><b>What is erased:</b>
     * <ul>
     *   <li>All user first/last names → "[deleted]"
     *   <li>All user email addresses → "[deleted-{userId}]@deleted.invalid"
     *   <li>All user password hashes → a fixed bcrypt hash of a random string
     *       (account becomes permanently inaccessible)
     *   <li>All customer names → "[deleted]"
     *   <li>All customer email, phone, address, notes → null
     *   <li>Tenant name → "[deleted-{tenantId}]"
     *   <li>Tenant status → SUSPENDED (prevents new logins)
     * </ul>
     *
     * <p><b>What is retained:</b> order records, stock movements, audit log entries
     * (with actor_email already denormalised — those are anonymised separately by
     * the audit log retention job). IDs and timestamps are retained for referential
     * integrity and legal compliance.
     *
     * @param tenantId    the tenant to erase
     * @param confirmSlug the tenant's slug, supplied by the caller as confirmation
     */
    public void eraseTenant(String tenantId, String confirmSlug) {
        Tenant tenant = load(tenantId);

        // Require explicit slug confirmation to prevent accidental erasure
        if (!tenant.getSlug().equals(confirmSlug)) {
            throw new InvalidRequestException(
                    "Confirmation slug does not match. Supply your exact tenant slug to confirm erasure.");
        }

        log.warn("GDPR erasure initiated for tenant [{}] slug [{}]", tenantId, tenant.getSlug());

        userRepository.findAllByTenantId(tenantId).forEach(user -> {
            user.setFirstName("[deleted]");
            user.setLastName("[deleted]");
            user.setEmail("[deleted-" + user.getId() + "]@deleted.invalid");
            // Overwrite the password hash with a freshly encoded random value so the
            // account can never be authenticated against again. Encoding a random UUID
            // (rather than storing a hardcoded literal) avoids shipping a constant
            // credential-like string in the source and keeps each erased account's
            // hash unique.
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            userRepository.save(user);
            // Overwriting the password hash only blocks re-authentication; any refresh token
            // issued before erasure would still mint fresh access tokens for up to 7 days.
            // Revoke them so erasure takes effect immediately rather than eventually.
            refreshTokenRepository.revokeAllByUserId(user.getId());
        });

        customerRepository.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged())
                .forEach(customer -> {
                    customer.setName("[deleted]");
                    customer.setEmail(null);
                    customer.setPhone(null);
                    customer.setAddress(null);
                    customer.setNotes(null);
                    customerRepository.save(customer);
                });

        tenant.setName("[deleted-" + tenantId + "]");
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        log.warn("GDPR erasure complete for tenant [{}] — all PII pseudonymised, account suspended", tenantId);
    }

    private Tenant load(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }
}
