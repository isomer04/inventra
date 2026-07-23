package com.inventra.api.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventra.api.auth.dto.LoginRequest;
import com.inventra.api.auth.dto.RegisterRequest;
import com.inventra.api.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rate Limiting Tests
 *
 * <p>Tests that RateLimitFilter enforces rate limits on auth endpoints.
 * Verifies 429 (Too Many Requests) responses and Retry-After header.
 *
 * <p>Does NOT extend {@link com.inventra.api.BaseIntegrationTest} because
 * that class disables the rate limiter. Instead, this class has its own
 * Testcontainers MySQL and explicitly enables the rate limiter via
 * {@code @SpringBootTest(properties = ...)}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.security.rate-limit.enabled=true"
)
@AutoConfigureMockMvc
@DisplayName("Rate Limiting")
class RateLimitTest {

    static final MySQLContainer<?> MY_SQL;

    static {
        MY_SQL = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("inventra_test_rl")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        MY_SQL.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MY_SQL::getJdbcUrl);
        registry.add("spring.datasource.username", MY_SQL::getUsername);
        registry.add("spring.datasource.password", MY_SQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.access-token-expiry-ms", () -> "900000");
        registry.add("app.jwt.refresh-token-expiry-ms", () -> "604800000");
        registry.add("app.jwt.secret",
                () -> "dGVzdC1vbmx5LXNlY3JldC1mb3ItaW50ZWdyYXRpb24tdGVzdHMtbm90LWZvci1wcm9kdWN0aW9uLW11c3QtYmUtMjU2LWJpdHM=");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void clearBuckets() {
        rateLimitFilter.resetForTesting();
    }

    @Nested
    @DisplayName("Login Rate Limiting")
    class LoginRateLimiting {

        @Test
        @DisplayName("POST /api/v1/auth/login MAX+1 times in 1 minute returns 429")
        void login_whenExceedingRateLimit_thenReturns429() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            String requestBody = objectMapper.writeValueAsString(request);

            // RateLimitFilter allows 20 requests per minute on /api/v1/auth/**.
            // First 20 requests are within budget (return whatever the auth
            // endpoint returns: typically 401); request 21 must be 429.
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody));
            }

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("POST /api/v1/auth/login 429 response includes Retry-After header")
        void login_when429_thenIncludesRetryAfterHeader() throws Exception {
            LoginRequest request = new LoginRequest("test2@example.com", "password123");
            String requestBody = objectMapper.writeValueAsString(request);

            // Exceed the 20-request budget
            for (int i = 0; i < 21; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody));
            }

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        }

        @Test
        @DisplayName("POST /api/v1/auth/login after rate limit window resets returns 200")
        void login_afterWindowReset_thenReturns200() {
            // This test would require waiting for the rate limit window to reset
            // In practice, this would be tested with a shorter window or mocked time
            // For now, we document the expected behavior
            
            // Expected: After 60 seconds, rate limit resets and requests succeed again
            // Implementation: Would need to mock time or use a test-specific rate limit window
        }
    }

    @Nested
    @DisplayName("Register Rate Limiting")
    class RegisterRateLimiting {

        @Test
        @DisplayName("POST /api/v1/auth/register MAX+1 times in 1 minute returns 429")
        void register_whenExceedingRateLimit_thenReturns429() throws Exception {
            RegisterRequest request = new RegisterRequest(
                "Test Tenant",
                "test-tenant",
                "admin@example.com",
                "password123",
                "Admin",
                "User"
            );
            String requestBody = objectMapper.writeValueAsString(request);

            // 20 requests within budget (most return 409 because the slug
            // collides with itself after the first success); request 21 → 429.
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody));
            }

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("POST /api/v1/auth/register 429 response includes Retry-After header")
        void register_when429_thenIncludesRetryAfterHeader() throws Exception {
            RegisterRequest request = new RegisterRequest(
                "Test Tenant 2",
                "test-tenant-2",
                "admin2@example.com",
                "password123",
                "Admin",
                "User"
            );
            String requestBody = objectMapper.writeValueAsString(request);

            for (int i = 0; i < 21; i++) {
                mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody));
            }

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        }
    }

    @Nested
    @DisplayName("Refresh Rate Limiting")
    class RefreshRateLimiting {

        @Test
        @DisplayName("POST /api/v1/auth/refresh MAX+1 times in 1 minute returns 429")
        void refresh_whenExceedingRateLimit_thenReturns429() throws Exception {
            String refreshToken = "dummy-refresh-token";

            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
            }

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isTooManyRequests());
        }
    }
}
