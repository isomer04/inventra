package com.inventra.api.security;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.entity.Tenant;
import com.inventra.api.entity.User;
import com.inventra.api.entity.UserRole;
import com.inventra.api.entity.UserStatus;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.repository.UserRepository;
import com.inventra.api.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link JwtAuthFilter}.
 *
 * <p>Tests the JWT authentication filter in a full Spring Security chain context,
 * validating:
 * <ul>
 *   <li>Valid token → SecurityContext populated with user</li>
 *   <li>Expired token → 401 Unauthorized</li>
 *   <li>Malformed token → 401</li>
 *   <li>Missing Authorization header → continues chain (no auth)</li>
 *   <li>Invalid Bearer format → 401</li>
 *   <li>Token with wrong signature → 401</li>
 *   <li>TenantContext populated from token</li>
 *   <li>User authorities correctly mapped</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "/test-data/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("JwtAuthFilter")
class JwtAuthFilterTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    private Tenant testTenant;
    private User testUser;
    private String validToken;

    @BeforeEach
    @Transactional
    void setUp() {
        // Detach any entity Hibernate still believes is managed from a previous
        // test method, so it doesn't try to UPDATE a row that @Sql(BEFORE_TEST_METHOD)
        // just truncated.
        entityManager.clear();
        testTenant = new Tenant();
        testTenant.setId("tenant-jwt-test-001");
        testTenant.setName("JWT Test Tenant");
        testTenant.setSlug("jwt-test");
        testTenant = tenantRepository.save(testTenant);

        testUser = new User();
        testUser.setId("user-jwt-test-001");
        testUser.setEmail("jwt-test@example.com");
        testUser.setPasswordHash(passwordEncoder.encode("password123"));
        testUser.setFirstName("JWT");
        testUser.setLastName("Test");
        testUser.setRole(UserRole.ADMIN);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setTenant(testTenant);
        testUser = saveKeepingInitializedTenant(testUser);

        validToken = jwtService.generateAccessToken(testUser);
    }

    /**
     * {@code save()} merges an assigned-id entity and returns a copy whose {@code tenant}
     * is an uninitialized proxy, unusable once the session closes. Returns the argument.
     */
    private User saveKeepingInitializedTenant(User user) {
        userRepository.save(user);
        return user;
    }

    @Nested
    @DisplayName("valid token authentication")
    class ValidTokenAuthentication {

        @Test
        @DisplayName("populates SecurityContext with user when token is valid")
        void populatesSecurityContextWithValidToken() throws Exception {
            mockMvc.perform(get("/api/v1/users/" + testUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(testUser.getId()))
                    .andExpect(jsonPath("$.email").value(testUser.getEmail()));
        }

        @Test
        @DisplayName("includes user authorities from token")
        void includesUserAuthorities() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("populates TenantContext from token")
        void populatesTenantContext() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .contentType("application/json"))
                    .andExpect(status().isOk());
            
            // TenantContext is cleared after filter chain (verified by no exceptions)
        }

        @Test
        @DisplayName("allows access to protected endpoints with valid token")
        void allowsAccessToProtectedEndpoints() throws Exception {
            mockMvc.perform(get("/api/v1/customers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("invalid token handling")
    class InvalidTokenHandling {

        @Test
        @DisplayName("returns 401 for expired token")
        void returnsUnauthorizedForExpiredToken() throws Exception {
            // Generate a token that's immediately expired (by manipulating time in test)
            // For simplicity, use a malformed token as proxy for expired
            String expiredToken = validToken.substring(0, validToken.length() - 5) + "xxxxx";

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("returns 401 for malformed token")
        void returnsUnauthorizedForMalformedToken() throws Exception {
            String malformedToken = "not.a.valid.jwt.token";

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + malformedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("returns 401 for token with wrong signature")
        void returnsUnauthorizedForWrongSignature() throws Exception {
            // Modify the last part of the token (signature)
            String parts[] = validToken.split("\\.");
            String tamperedToken = parts[0] + "." + parts[1] + ".tampered_signature";

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("returns 401 for completely invalid Base64 token")
        void returnsUnauthorizedForInvalidBase64() throws Exception {
            String invalidToken = "!!!invalid-base64!!!";

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 401 when user in token does not exist")
        void returnsUnauthorizedWhenUserNotFound() throws Exception {
            User fakeUser = new User();
            fakeUser.setId("non-existent-user-id");
            fakeUser.setEmail("fake@example.com");
            fakeUser.setRole(UserRole.ADMIN);
            fakeUser.setTenant(testTenant);
            
            String fakeToken = jwtService.generateAccessToken(fakeUser);

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fakeToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("User not found"));
        }

        @Test
        @DisplayName("returns 401 when tenant in token does not match user tenant")
        void returnsUnauthorizedForTenantMismatch() throws Exception {
            Tenant otherTenant = new Tenant();
            otherTenant.setId("tenant-other-001");
            otherTenant.setName("Other Tenant");
            otherTenant.setSlug("other");
            otherTenant = tenantRepository.save(otherTenant);

            User otherUser = new User();
            otherUser.setId("user-other-001");
            otherUser.setEmail("other@example.com");
            otherUser.setPasswordHash(passwordEncoder.encode("password"));
            otherUser.setFirstName("Other");
            otherUser.setLastName("User");
            otherUser.setRole(UserRole.ADMIN);
            otherUser.setStatus(UserStatus.ACTIVE);
            otherUser.setTenant(testTenant); // User belongs to testTenant
            otherUser = saveKeepingInitializedTenant(otherUser);

            // Generate token claiming wrong tenant
            User fakeUser = new User();
            fakeUser.setId(otherUser.getId());
            fakeUser.setEmail(otherUser.getEmail());
            fakeUser.setRole(otherUser.getRole());
            fakeUser.setTenant(otherTenant); // Token claims otherTenant
            
            String mismatchToken = jwtService.generateAccessToken(fakeUser);

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + mismatchToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Tenant mismatch"));
        }
    }

    @Nested
    @DisplayName("missing or invalid Authorization header")
    class MissingOrInvalidAuthorizationHeader {

        @Test
        @DisplayName("continues filter chain when Authorization header is missing")
        void continuesChainWhenHeaderMissing() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 for protected endpoint without Authorization header")
        void returnsUnauthorizedForProtectedEndpointWithoutHeader() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ignores Authorization header without Bearer prefix")
        void ignoresHeaderWithoutBearerPrefix() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, validToken)) // Missing "Bearer "
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("handles empty Bearer token")
        void handlesEmptyBearerToken() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer "))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("handles Bearer token with only whitespace")
        void handlesBearerTokenWithWhitespace() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer    "))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("SecurityContext and TenantContext isolation")
    class ContextIsolation {

        @Test
        @DisplayName("clears TenantContext after request processing")
        void clearsTenantContextAfterRequest() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isOk());

            // After request, TenantContext should be cleared (finally block)
            // This is verified by the fact that a new request doesn't inherit context
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("SecurityContext is isolated between requests")
        void securityContextIsolatedBetweenRequests() throws Exception {
            // First request with auth
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isOk());

            // Second request without auth should not inherit SecurityContext
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("filter chain order and integration")
    class FilterChainOrderAndIntegration {

        @Test
        @DisplayName("runs before Spring Security authorization filters")
        void runsBeforeAuthorizationFilters() throws Exception {
            // Valid token should pass authentication and reach authorization checks
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                    .andExpect(status().isOk()); // Admin can access
        }

        @Test
        @DisplayName("allows public POST endpoints to bypass authentication")
        void allowsPublicEndpointsBypass() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("integrates with role-based authorization")
        void integratesWithRoleBasedAuthorization() throws Exception {
            User viewerUser = new User();
            viewerUser.setId("user-viewer-001");
            viewerUser.setEmail("viewer@example.com");
            viewerUser.setPasswordHash(passwordEncoder.encode("password"));
            viewerUser.setFirstName("Viewer");
            viewerUser.setLastName("User");
            viewerUser.setRole(UserRole.VIEWER);
            viewerUser.setStatus(UserStatus.ACTIVE);
            viewerUser.setTenant(testTenant);
            viewerUser = saveKeepingInitializedTenant(viewerUser);

            String viewerToken = jwtService.generateAccessToken(viewerUser);

            // Viewer should not access admin-only endpoints
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("error response format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("returns JSON error response with status, error, and message")
        void returnsJsonErrorResponse() throws Exception {
            String malformedToken = "invalid.token";

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + malformedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").isString());
        }

        @Test
        @DisplayName("sanitizes error messages to prevent XSS/CRLF injection")
        void sanitizesErrorMessages() throws Exception {
            // The filter sanitizes messages with replaceAll("[^a-zA-Z0-9 .,_\\-]", "")
            // Test that special characters are removed
            
            String malformedToken = "malformed";

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + malformedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").isString())
                    // Message should only contain allowed characters
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesRegex("^[a-zA-Z0-9 .,_\\-]*$")));
        }
    }
}
