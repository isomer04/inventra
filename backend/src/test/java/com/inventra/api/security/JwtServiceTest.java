package com.inventra.api.security;

import com.inventra.api.config.JwtProperties;
import com.inventra.api.entity.Tenant;
import com.inventra.api.entity.TenantStatus;
import com.inventra.api.entity.User;
import com.inventra.api.entity.UserRole;
import com.inventra.api.entity.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 *
 * No Spring context — the service is constructed directly with a test
 * {@link JwtProperties} record. Gaps closed: G-005 (expiration edge case),
 * G-006 (malformed / tampered tokens).
 */
@DisplayName("JwtService")
class JwtServiceTest {

    /**
     * 72-byte test secret (same baseline used in integration tests).
     * Decodes to the ASCII string "test-only-secret-for-integration-tests-not-
     * for-production-must-be-256-bits", which is 72 bytes — well above the
     * HMAC-SHA256 minimum of 32 bytes.
     */
    private static final String TEST_SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1mb3ItaW50ZWdyYXRpb24tdGVzdHMtbm90LWZvci1wcm9kdWN0aW9uLW11c3QtYmUtMjU2LWJpdHM=";
    private static final long ACCESS_EXPIRY_MS  = 900_000L;    // 15 min
    private static final long REFRESH_EXPIRY_MS = 86_400_000L; // 24 h

    private JwtService service;

    /** Signing key derived the same way JwtService does in its constructor. */
    private SecretKey testKey;

    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(TEST_SECRET, ACCESS_EXPIRY_MS, REFRESH_EXPIRY_MS);
        service = new JwtService(props);
        testKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Tenant")
                .slug("test-" + UUID.randomUUID().toString().substring(0, 6))
                .status(TenantStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID().toString())
                .tenant(tenant)
                .email("unit-test@example.test")
                .passwordHash("$2a$10$irrelevant-hash-not-used-here")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessToken {

        @Test
        @DisplayName("returns a three-part JWT string")
        void returnsThreePartJwt() {
            String token = service.generateAccessToken(testUser);

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("generated token is immediately valid")
        void generatedTokenIsValid() {
            String token = service.generateAccessToken(testUser);

            assertThat(service.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("subject claim equals user id")
        void subjectClaimEqualsUserId() {
            String token = service.generateAccessToken(testUser);

            assertThat(service.extractUserId(token)).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("tenantId claim equals tenant id")
        void tenantIdClaimEqualsTenantId() {
            String token = service.generateAccessToken(testUser);

            assertThat(service.extractTenantId(token)).isEqualTo(testUser.getTenant().getId());
        }
    }

    @Nested
    @DisplayName("isTokenValid — expiration (G-005)")
    class ExpirationEdgeCases {

        @Test
        @DisplayName("returns false when token expired 1 ms ago")
        void returnsFalse_whenExpiredByOneMillisecond() {
            String expiredToken = Jwts.builder()
                    .issuer(JwtService.ISSUER)
                    .audience().add(JwtService.AUDIENCE).and()
                    .subject(testUser.getId())
                    .claim("tenantId", testUser.getTenant().getId())
                    .claim("tenantSlug", testUser.getTenant().getSlug())
                    .claim("roles", List.of(UserRole.ADMIN.name()))
                    .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                    .expiration(Date.from(Instant.now().minusMillis(1)))
                    .signWith(testKey)
                    .compact();

            assertThat(service.isTokenValid(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("returns false for a token expired 1 hour ago")
        void returnsFalse_whenExpiredOneHourAgo() {
            String longExpiredToken = Jwts.builder()
                    .issuer(JwtService.ISSUER)
                    .audience().add(JwtService.AUDIENCE).and()
                    .subject(testUser.getId())
                    .claim("tenantId", testUser.getTenant().getId())
                    .claim("tenantSlug", testUser.getTenant().getSlug())
                    .claim("roles", List.of(UserRole.ADMIN.name()))
                    .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                    .expiration(Date.from(Instant.now().minusSeconds(3600)))
                    .signWith(testKey)
                    .compact();

            assertThat(service.isTokenValid(longExpiredToken)).isFalse();
        }

        @Test
        @DisplayName("returns true when token expires in 5 seconds")
        void returnsTrue_whenTokenExpiresInOneSec() {
            // Use 5 seconds to avoid flakiness on slow CI runners where 1 second
            // could elapse between token creation and the isTokenValid call.
            String almostExpiredToken = Jwts.builder()
                    .issuer(JwtService.ISSUER)
                    .audience().add(JwtService.AUDIENCE).and()
                    .subject(testUser.getId())
                    .claim("tenantId", testUser.getTenant().getId())
                    .claim("tenantSlug", testUser.getTenant().getSlug())
                    .claim("roles", List.of(UserRole.ADMIN.name()))
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(5)))
                    .signWith(testKey)
                    .compact();

            assertThat(service.isTokenValid(almostExpiredToken)).isTrue();
        }
    }

    @Nested
    @DisplayName("isTokenValid — malformed/tampered tokens (G-006)")
    class MalformedTokens {

        @Test
        @DisplayName("returns false when signature is tampered")
        void returnsFalse_whenSignatureTampered() {
            String validToken = service.generateAccessToken(testUser);
            int dotIdx = validToken.lastIndexOf('.');
            // Replace the entire signature with a random valid base64url string of the
            // same length. Flipping a single char can theoretically still verify if
            // the original signature happened to differ only in trailing padding
            // bits; replacing the whole segment guarantees the HMAC mismatches.
            String origSig = validToken.substring(dotIdx + 1);
            StringBuilder tamperedSig = new StringBuilder(origSig.length());
            for (int i = 0; i < origSig.length(); i++) {
                char c = origSig.charAt(i);
                tamperedSig.append(c == 'A' ? 'B' : 'A');
            }
            String tampered = validToken.substring(0, dotIdx + 1) + tamperedSig;

            assertThat(service.isTokenValid(tampered)).isFalse();
        }

        @Test
        @DisplayName("returns false for alg:none unsigned token (JWT attack)")
        void returnsFalse_whenAlgorithmNone() {
            // Craft an unsigned JWT (alg:none attack — JJWT 0.12+ rejects these)
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
            long futureExp = Instant.now().getEpochSecond() + 3600;
            String payloadJson = "{\"sub\":\"" + testUser.getId() + "\","
                    + "\"iss\":\"" + JwtService.ISSUER + "\","
                    + "\"aud\":[\"" + JwtService.AUDIENCE + "\"],"
                    + "\"exp\":" + futureExp + "}";
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes());
            String algNoneToken = header + "." + payload + "."; // empty signature

            assertThat(service.isTokenValid(algNoneToken)).isFalse();
        }

        @Test
        @DisplayName("returns false for completely malformed string")
        void returnsFalse_whenCompletelyMalformed() {
            assertThat(service.isTokenValid("not-a-jwt")).isFalse();
            assertThat(service.isTokenValid("only.two")).isFalse();
            assertThat(service.isTokenValid("")).isFalse();
        }

        @Test
        @DisplayName("returns false when issuer claim is missing")
        void returnsFalse_whenIssuerClaimMissing() {
            // Valid signature, valid audience, but no iss claim
            String tokenWithoutIss = Jwts.builder()
                    .audience().add(JwtService.AUDIENCE).and()
                    .subject(testUser.getId())
                    .claim("tenantId", testUser.getTenant().getId())
                    .claim("tenantSlug", testUser.getTenant().getSlug())
                    .claim("roles", List.of(UserRole.ADMIN.name()))
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(testKey)
                    .compact();

            assertThat(service.isTokenValid(tokenWithoutIss)).isFalse();
        }

        @Test
        @DisplayName("returns false when issuer claim has wrong value")
        void returnsFalse_whenIssuerClaimWrong() {
            String tokenWrongIss = Jwts.builder()
                    .issuer("attacker-controlled-issuer")
                    .audience().add(JwtService.AUDIENCE).and()
                    .subject(testUser.getId())
                    .claim("tenantId", testUser.getTenant().getId())
                    .claim("tenantSlug", testUser.getTenant().getSlug())
                    .claim("roles", List.of(UserRole.ADMIN.name()))
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(testKey)
                    .compact();

            assertThat(service.isTokenValid(tokenWrongIss)).isFalse();
        }

        @Test
        @DisplayName("returns false when audience claim is missing")
        void returnsFalse_whenAudienceClaimMissing() {
            // Valid signature, valid issuer, but no aud claim
            String tokenWithoutAud = Jwts.builder()
                    .issuer(JwtService.ISSUER)
                    .subject(testUser.getId())
                    .claim("tenantId", testUser.getTenant().getId())
                    .claim("tenantSlug", testUser.getTenant().getSlug())
                    .claim("roles", List.of(UserRole.ADMIN.name()))
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(testKey)
                    .compact();

            assertThat(service.isTokenValid(tokenWithoutAud)).isFalse();
        }

        @Test
        @DisplayName("returns false when audience claim has wrong value")
        void returnsFalse_whenAudienceClaimWrong() {
            String tokenWrongAud = Jwts.builder()
                    .issuer(JwtService.ISSUER)
                    .audience().add("wrong-service").and()
                    .subject(testUser.getId())
                    .claim("tenantId", testUser.getTenant().getId())
                    .claim("tenantSlug", testUser.getTenant().getSlug())
                    .claim("roles", List.of(UserRole.ADMIN.name()))
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(testKey)
                    .compact();

            assertThat(service.isTokenValid(tokenWrongAud)).isFalse();
        }
    }

    @Nested
    @DisplayName("hashToken")
    class HashToken {

        @Test
        @DisplayName("same input always produces same hash (deterministic)")
        void isDeterministic() {
            String raw = UUID.randomUUID().toString();

            assertThat(service.hashToken(raw)).isEqualTo(service.hashToken(raw));
        }

        @Test
        @DisplayName("different inputs produce different hashes (collision resistance)")
        void differentInputsProduceDifferentHashes() {
            assertThat(service.hashToken("token-a")).isNotEqualTo(service.hashToken("token-b"));
        }

        @Test
        @DisplayName("output is a 64-character lowercase hex string (SHA-256)")
        void outputIs64CharLowercaseHex() {
            String hash = service.hashToken("any-refresh-token");

            assertThat(hash)
                    .hasSize(64)
                    .matches("[0-9a-f]+");
        }
    }

    @Nested
    @DisplayName("generateRawRefreshToken")
    class GenerateRawRefreshToken {

        @Test
        @DisplayName("returns a non-blank string")
        void returnsNonBlankString() {
            assertThat(service.generateRawRefreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("successive calls return different values (uniqueness)")
        void successiveCallsReturnDifferentValues() {
            String first  = service.generateRawRefreshToken();
            String second = service.generateRawRefreshToken();

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("constructor key validation")
    class ConstructorKeyValidation {

        @Test
        @DisplayName("throws when secret decodes to fewer than 32 bytes")
        void throwsWhenSecretTooShort() {
            // 31 zero-bytes — below HMAC-SHA256 minimum
            String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);
            JwtProperties shortProps = new JwtProperties(shortSecret, 900_000L, 86_400_000L);

            // JwtService validates the key length in its constructor and JJWT's
            // Keys.hmacShaKeyFor() also rejects short keys — either way, construction fails.
            assertThatThrownBy(() -> new JwtService(shortProps))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("succeeds when secret is exactly 32 bytes")
        void succeedsWhenSecretIsMinimumLength() {
            // Exactly 32 zero-bytes — meets HMAC-SHA256 minimum
            String minSecret = Base64.getEncoder().encodeToString(new byte[32]);
            JwtProperties minProps = new JwtProperties(minSecret, 900_000L, 86_400_000L);

            // Should construct without throwing
            JwtService minService = new JwtService(minProps);
            assertThat(minService).isNotNull();
        }
    }
}
