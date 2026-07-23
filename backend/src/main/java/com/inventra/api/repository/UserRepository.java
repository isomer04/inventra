package com.inventra.api.repository;

import com.inventra.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find a user by email address, searching across ALL tenants.
     *
     * <p><b>Cross-tenant contract:</b> this method intentionally crosses
     * tenant boundaries. It is used exclusively during authentication (login), where the
     * tenant is not yet known from the JWT.
     *
     * <p>Do NOT call this method from a service context where a tenant is already
     * established — use {@link #findByIdAndTenantId} or {@link #findAllByTenantId} instead.
     *
     * <p>Email is PII (GDPR Art. 4). Do not log or expose the result beyond the auth flow.
     */
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u JOIN FETCH u.tenant WHERE u.id = :id")
    Optional<User> findByIdWithTenant(String id);

    @Query("SELECT u FROM User u JOIN FETCH u.tenant WHERE u.tenant.id = :tenantId")
    List<User> findAllByTenantId(String tenantId);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.tenant.id = :tenantId")
    Optional<User> findByIdAndTenantId(String id, String tenantId);

    /**
     * Whether any user anywhere already uses this email address.
     *
     * <p><b>Cross-tenant contract:</b> deliberately global, mirroring the
     * {@code uk_user_email} unique constraint, which is not tenant-scoped. Used for a
     * friendly pre-check before insert; the constraint remains the real guarantee
     * (see the {@code DataIntegrityViolationException} handler for the race).
     */
    boolean existsByEmail(String email);
}
