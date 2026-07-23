package com.inventra.api.auth;

import com.inventra.api.audit.AuditEventType;
import com.inventra.api.audit.AuditPayload;
import com.inventra.api.audit.AuditService;
import com.inventra.api.auth.dto.LoginRequest;
import com.inventra.api.auth.dto.RegisterRequest;
import com.inventra.api.auth.dto.TokenResponse;
import com.inventra.api.entity.RefreshToken;
import com.inventra.api.entity.Tenant;
import com.inventra.api.entity.TenantStatus;
import com.inventra.api.entity.User;
import com.inventra.api.entity.UserRole;
import com.inventra.api.entity.UserStatus;
import com.inventra.api.exception.DuplicateResourceException;
import com.inventra.api.repository.RefreshTokenRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.repository.UserRepository;
import com.inventra.api.security.JwtService;
import com.inventra.api.util.LogSanitizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * Decoy hash verified on the unknown-email login path so it costs the same as the
     * known-email path (see {@link #login}). Computed at startup by encoding a random
     * value rather than hardcoded, so it always matches the configured encoder's cost
     * factor and no constant credential-like string ships in the source.
     */
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyPasswordHash() {
        dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public TokenResponse register(RegisterRequest req) {
        if (tenantRepository.existsBySlug(req.slug())) {
            throw new DuplicateResourceException("Slug already taken: " + req.slug());
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(req.tenantName())
                .slug(req.slug())
                .build());

        User user = userRepository.save(User.builder()
                .tenant(tenant)
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .firstName(req.firstName())
                .lastName(req.lastName())
                .role(UserRole.ADMIN)
                .build());

        log.info("Registered new tenant [{}] with admin user [{}]", LogSanitizer.sanitize(tenant.getSlug()), LogSanitizer.sanitize(user.getId()));
        auditService.record(user.getId(), null, AuditEventType.TENANT_REGISTERED.toString(), "Tenant", tenant.getId(),
                null, AuditPayload.of("slug", tenant.getSlug()));
        return issueTokenPair(user);
    }

    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElse(null);

        if (user == null) {
            // Burn one BCrypt verification against a dummy hash before failing. Returning
            // immediately here made the unknown-email path ~100ms faster than the known-email
            // path, which reliably distinguishes registered from unregistered addresses
            // (OWASP Authentication Cheat Sheet). The error message was already uniform;
            // the response time now is too.
            passwordEncoder.matches(req.password(), dummyPasswordHash);
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Account is inactive");
        }
        if (user.getTenant().getStatus() != TenantStatus.ACTIVE) {
            throw new BadCredentialsException("Tenant account is suspended");
        }

        return issueTokenPair(user);
    }

    /**
     * {@code noRollbackFor} is what makes reuse detection stick. Every rejection path below
     * throws {@link BadCredentialsException}, and under the class-level {@code @Transactional}
     * that marked the transaction rollback-only — silently undoing the family revocation
     * {@link #revokeTokenFamily} had just performed, so a detected-as-stolen token chain
     * stayed usable. Suppressing rollback for this one exception keeps the revocation (and
     * the consume below) committed. Nothing on these paths needs undoing: a refresh token
     * that was presented and rejected is correct to leave revoked.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenResponse refresh(String rawToken) {
        String hash = jwtService.hashToken(rawToken);
        Instant now = Instant.now();

        // Read user data before consuming the token so we
        // never need to read a revoked token entity after the fact.
        // findActiveByTokenHash does a single SELECT with user+tenant eagerly
        // loaded and verifies revoked=false + expiresAt>now in the query itself.
        RefreshToken stored = refreshTokenRepository.findActiveByTokenHash(hash, now)
                .orElseThrow(() -> {
                    detectTokenReuse(hash, now);
                    return new BadCredentialsException("Refresh token is invalid, expired, or already used");
                });

        // Atomic consume: UPDATE sets revoked=true only if the token is still
        // active. Under MySQL REPEATABLE READ this is the definitive serialisation
        // point — only one concurrent request can get consumed == 1.
        int consumed = refreshTokenRepository.consumeIfActive(hash, now);
        if (consumed == 0) {
            // The SELECT above saw this token active and the UPDATE did not, so a concurrent
            // request consumed it in the gap. Expiry cannot explain the difference — both
            // queries are given the same `now`, so their expiry predicates are identical.
            // That leaves exactly one cause: two parties presented the same refresh token,
            // which is the reuse signature detectTokenReuse exists for. Reject this request
            // *and* burn the family; rejecting alone leaves the other holder's chain alive.
            //
            // Detection is inline rather than via detectTokenReuse(hash, now) because we
            // already hold the owning user, and a re-read here would return this
            // transaction's REPEATABLE READ snapshot — taken before the concurrent commit,
            // so it would still show revoked=false and detect nothing.
            revokeTokenFamily(stored.getUser().getId());
            throw new BadCredentialsException("Refresh token is invalid, expired, or already used");
        }

        if (stored.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Account is inactive");
        }
        if (stored.getUser().getTenant().getStatus() != TenantStatus.ACTIVE) {
            throw new BadCredentialsException("Tenant account is suspended");
        }

        return issueTokenPair(stored.getUser());
    }

    /**
     * Replaying an already-consumed refresh token is the canonical indicator that the token
     * family was stolen (RFC 6749 §10.4, OAuth 2.0 Security BCP §4.14.2): the legitimate
     * client and the attacker are both holding descendants of one chain. Rejecting just this
     * request leaves the attacker's rotated chain alive indefinitely, so revoke the whole
     * family and force every device to re-authenticate.
     *
     * <p>Only fires for a token that exists but is revoked. An unknown hash is not evidence
     * of anything (and identifies no user), and natural expiry is not an attack.
     */
    private void detectTokenReuse(String hash, Instant now) {
        refreshTokenRepository.findByTokenHash(hash)
                .filter(t -> t.isRevoked() && t.getExpiresAt().isAfter(now))
                .ifPresent(reused -> revokeTokenFamily(reused.getUser().getId()));
    }

    /** Revokes every session for a user after reuse has been established. */
    private void revokeTokenFamily(String userId) {
        log.warn("SECURITY: refresh token reuse detected for user [{}] — revoking all sessions",
                LogSanitizer.sanitize(userId));
        refreshTokenRepository.revokeAllByUserId(userId);
        auditService.record(userId, null, AuditEventType.REFRESH_TOKEN_REUSE_DETECTED.toString(),
                "User", userId, null, AuditPayload.of("allSessionsRevoked", true));
    }

    /**
     * Idempotent by contract: revoke the token if we know it, return success either way.
     * Throwing 404 for an unknown token turned logout into an existence oracle for token
     * hashes and made the common double-logout case fail for no benefit.
     */
    public void logout(String rawToken) {
        String hash = jwtService.hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(stored -> {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
        });
    }

    private TokenResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = jwtService.generateRawRefreshToken();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tenant(user.getTenant())
                .tokenHash(jwtService.hashToken(rawRefresh))
                .expiresAt(Instant.now().plusMillis(jwtService.refreshTokenExpiryMs()))
                .revoked(false)
                .build());

        return new TokenResponse(accessToken, rawRefresh, "Bearer",
                jwtService.accessTokenExpiryMs() / 1000);
    }
}
