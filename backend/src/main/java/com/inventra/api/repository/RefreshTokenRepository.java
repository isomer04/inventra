package com.inventra.api.repository;

import com.inventra.api.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Loads an active (non-revoked, non-expired) refresh token with its user
     * and tenant eagerly fetched in a single query.
     *
     * <p>Eliminates the post-consume read-back in
     * {@link com.inventra.api.auth.AuthService#refresh}. The caller reads user
     * data here (optimistic check), then calls {@link #consumeIfActive} to
     * atomically revoke. This removes the confusing pattern of reading a
     * revoked token to obtain user details.
     *
     * @param tokenHash SHA-256 hex hash of the raw refresh token
     * @param now       current instant used for the expiry check
     * @return the active token with user+tenant loaded, or empty if not found / already used
     */
    @Query("""
           SELECT r FROM RefreshToken r
             JOIN FETCH r.user u
             JOIN FETCH u.tenant
            WHERE r.tokenHash = :tokenHash
              AND r.revoked   = false
              AND r.expiresAt > :now
           """)
    Optional<RefreshToken> findActiveByTokenHash(String tokenHash, Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE RefreshToken r
              SET r.revoked = true
            WHERE r.tokenHash = :tokenHash
              AND r.revoked = false
              AND r.expiresAt > :now
           """)
    int consumeIfActive(String tokenHash, Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId")
    void revokeAllByUserId(String userId);

    /**
     * Hard-deletes every refresh token belonging to a user.
     *
     * <p>Required before deleting the user row: {@code fk_refresh_token_user} is
     * {@code ON DELETE RESTRICT} (V1 migration) and {@code User} has no cascading
     * association to its tokens, so a user who has ever logged in cannot be deleted while
     * any token row survives. Revoking is not sufficient — a revoked row still references
     * the user and still blocks the DELETE. Everywhere else (deactivation, password change,
     * reuse detection) use {@link #revokeAllByUserId} instead, which preserves the rows
     * for forensics.
     *
     * @return the number of rows deleted
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.user.id = :userId")
    int deleteAllByUserId(String userId);

    /**
     * Deletes all expired refresh tokens to prevent unbounded table growth.
     * Called periodically by {@link com.inventra.api.security.RefreshTokenCleanupService}.
     *
     * @param now tokens with expiresAt before this instant are deleted
     * @return the number of rows deleted
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpired(Instant now);
}
