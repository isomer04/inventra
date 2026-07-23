package com.inventra.api.security;

import com.inventra.api.config.JwtProperties;
import com.inventra.api.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public final class JwtService {

    /** HMAC-SHA256 requires a minimum 256-bit (32-byte) key. */
    private static final int MIN_KEY_BYTES = 32;

    /**
     * Issuer claim value embedded in every access token.
     * Adding iss/aud prevents token confusion when multiple
     * services share the same signing key (e.g. future microservices split).
     */
    static final String ISSUER   = "inventra-api";

    /**
     * Audience claim value — the intended consumer of the access token.
     */
    static final String AUDIENCE = "inventra-frontend";

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    /**
     * Validates the JWT signing key meets the minimum entropy requirement and
     * initializes the signing key.
     *
     * <p>Fail fast with a clear, actionable message before JJWT's own
     * {@code WeakKeyException} fires. A misconfigured (too-short) JWT secret must
     * prevent the application from starting — a weak HMAC key undermines all token
     * security.
     *
     * <p>OWASP JWT Cheat Sheet — secret must be ≥ 256 bits (32 bytes) for HS256.
     * Reference: https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html#secret-length-requirements
     *
     * <p>The class is {@code final} so that this validating constructor cannot be
     * exploited via a finalizer attack on a malicious subclass — which is the
     * concern SpotBugs raises with CT_CONSTRUCTOR_THROW. With no subclass possible,
     * throwing from the constructor is safe and is the idiomatic way to reject
     * invalid configuration at construction time.
     */
    public JwtService(JwtProperties props) {
        byte[] decoded = Decoders.BASE64.decode(props.secret());
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "JWT_SECRET is too short: decoded to " + decoded.length + " bytes, " +
                    "but HMAC-SHA256 requires at least " + MIN_KEY_BYTES + " bytes (256 bits). " +
                    "Generate a valid secret with: openssl rand -base64 32"
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(decoded);
        this.accessTokenExpiryMs  = props.accessTokenExpiryMs();
        this.refreshTokenExpiryMs = props.refreshTokenExpiryMs();
    }

    /**
     * Builds a signed access token. Requires {@code user.getTenant()} to be initialized —
     * this method opens no transaction of its own.
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        // Add iss and aud claims.
        // iss identifies this service as the token issuer.
        // aud restricts acceptance to the frontend — prevents a token
        // issued here from being accepted by a future backend-to-backend service.
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(user.getId())
                .claim("tenantId", user.getTenant().getId())
                .claim("tenantSlug", user.getTenant().getSlug())
                .claim("roles", List.of(user.getRole().name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiryMs)))
                .signWith(signingKey)
                .compact();
    }

    public String generateRawRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public long accessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    public long refreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    public String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractTenantId(String token) {
        return parseClaims(token).get("tenantId", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        // Enforce iss and aud on every parse.
        // Tokens without matching iss/aud will throw JwtException → rejected as invalid.
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
