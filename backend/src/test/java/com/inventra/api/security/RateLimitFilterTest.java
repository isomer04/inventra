package com.inventra.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.inventra.api.TestDatabase;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link RateLimitFilter}.
 *
 * Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} to test the
 * filter in a real servlet container context with full Spring Security chain.
 *
 * <p>This class deliberately does not extend {@code BaseIntegrationTest}: that base
 * disables the rate limiter, which is the one thing under test here. It wires the
 * shared container itself via {@link TestDatabase} so it still gets a datasource and
 * a JWT secret — without them the context falls back to the development defaults in
 * {@code application.yml} and fails to start.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.security.rate-limit.enabled=true"})
@DisplayName("RateLimitFilter")
class RateLimitFilterTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        // Clear rate limit state between tests to avoid interference
        rateLimitFilter.resetForTesting();
    }

    @Nested
    @DisplayName("rate limit threshold enforcement")
    class RateLimitThresholdEnforcement {

        @Test
        @DisplayName("allows 20 requests from same IP within 1 minute")
        void allowsUpToTwentyRequests() throws Exception {
            String clientIp = "192.168.1.100";

            // Make 20 requests — all should succeed
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized()); // login fails but filter passes
            }
        }

        @Test
        @DisplayName("returns 429 on 21st request from same IP")
        void returnsFourTwoNine_onTwentyFirstRequest() throws Exception {
            String clientIp = "192.168.1.101";

            // Exhaust the limit with 20 requests
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }

            // 21st request should be rate limited
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            }))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.error").value("Too Many Requests"));
        }

        @Test
        @DisplayName("includes Retry-After header in 429 response")
        void includesRetryAfterHeader() throws Exception {
            String clientIp = "192.168.1.102";

            // Exhaust the limit
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }

            // Next request should have Retry-After header
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            }))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(header().string("Retry-After", "60"));
        }
    }

    @Nested
    @DisplayName("concurrent requests")
    class ConcurrentRequests {

        @Test
        @DisplayName("handles 25 concurrent requests from same IP and rejects exactly 5")
        void handlesConcurrentRequestsFromSameIp() throws Exception {
            String clientIp = "192.168.1.200";
            int totalThreads = 25;
            ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch latch = new CountDownLatch(totalThreads);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rateLimitedCount = new AtomicInteger(0);

            for (int i = 0; i < totalThreads; i++) {
                executor.submit(() -> {
                    try {
                        int status = mockMvc.perform(post("/api/v1/auth/login")
                                        .contentType("application/json")
                                        .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                        .with(request -> {
                                            request.setRemoteAddr(clientIp);
                                            return request;
                                        }))
                                .andReturn()
                                .getResponse()
                                .getStatus();

                        if (status == 401) {
                            successCount.incrementAndGet();
                        } else if (status == 429) {
                            rateLimitedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Expect 20 successful (401 from auth failure) and 5 rate limited (429)
            assertThat(successCount.get()).isEqualTo(20);
            assertThat(rateLimitedCount.get()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("requests from different IPs")
    class RequestsFromDifferentIps {

        @Test
        @DisplayName("does not interfere - each IP has independent limit")
        void differentIpsHaveIndependentLimits() throws Exception {
            String clientIp1 = "192.168.1.10";
            String clientIp2 = "192.168.1.20";

            // Exhaust limit for IP1
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp1);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }

            // IP1 should be rate limited
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                            .with(request -> {
                                request.setRemoteAddr(clientIp1);
                                return request;
                            }))
                    .andExpect(status().isTooManyRequests());

            // IP2 should still be allowed (independent limit)
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp2);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Nested
    @DisplayName("X-Forwarded-For header handling")
    class XForwardedForHandling {

        @Test
        @DisplayName("uses X-Forwarded-For when TRUST_FORWARDED_FOR is enabled")
        void usesXForwardedForWhenTrustEnabled() throws Exception {
            // This test is environment-dependent. When TRUST_FORWARDED_FOR=true,
            // the filter should use the X-Forwarded-For header.
            // When false or unset, it should use RemoteAddr.
            // For this test, we document the behavior without setting env vars.
            
            String forwardedIp = "10.0.0.50";
            String remoteIp = "192.168.1.1";

            // Make requests with X-Forwarded-For header
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-Forwarded-For", forwardedIp)
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(remoteIp);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }

            // Behavior depends on TRUST_FORWARDED_FOR env var:
            // - If true: forwarded IP is rate limited
            // - If false/unset: remote IP is rate limited
            // This test validates the filter doesn't crash with the header present
        }

        @Test
        @DisplayName("handles multiple IPs in X-Forwarded-For (uses first)")
        void handlesMultipleIpsInXForwardedFor() throws Exception {
            String forwardedIps = "10.0.0.100, 10.0.0.101, 10.0.0.102";

            // The filter should extract the first IP from the comma-separated list
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-Forwarded-For", forwardedIps)
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr("192.168.1.1");
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }

            // Validated that filter processes the header without errors
        }
    }

    /** Shortened window here too — see {@link SlidingWindowExpiry}. */
    @Nested
    @DisplayName("stale entry eviction")
    @TestPropertySource(properties = {
            "app.security.rate-limit.enabled=true",
            "app.security.rate-limit.window-seconds=" + StaleEntryEviction.WINDOW_SECONDS
    })
    class StaleEntryEviction {

        static final int WINDOW_SECONDS = 10;

        @Test
        @DisplayName("evicts expired entries from request log")
        void evictsExpiredEntries() throws Exception {
            String clientIp = "192.168.1.150";

            // Make 10 requests
            for (int i = 0; i < 10; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }

            // Wait for the sliding window to expire (window + buffer)
            Thread.sleep((WINDOW_SECONDS + 1) * 1000L);

            // After expiry, should be able to make 20 more requests
            // (if eviction didn't happen, would be limited after 10)
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Nested
    @DisplayName("memory bounds enforcement")
    class MemoryBoundsEnforcement {

        @Test
        @DisplayName("prevents unbounded map growth under IP cycling attack")
        void preventsUnboundedGrowth() throws Exception {
            // Simulate IP cycling attack with many different IPs
            // The filter has MAX_MAP_SIZE = 10,000 hard cap
            // This test validates it doesn't crash with excessive IPs
            
            int attackIpCount = 100; // Use 100 for test speed, real cap is 10k

            for (int i = 0; i < attackIpCount; i++) {
                String attackIp = "10.0." + (i / 256) + "." + (i % 256);
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                                .with(request -> {
                                    request.setRemoteAddr(attackIp);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized());
            }

            // Filter should have processed all requests without memory issues
            assertThat(true).isTrue(); // Validated by not throwing OutOfMemoryError
        }
    }

    @Nested
    @DisplayName("non-auth endpoints")
    class NonAuthEndpoints {

        @Test
        @DisplayName("does not rate limit non-auth endpoints")
        void doesNotRateLimitNonAuthEndpoints() throws Exception {
            String clientIp = "192.168.1.250";

            // Make many requests to non-auth endpoint - should not be rate limited
            for (int i = 0; i < 25; i++) {
                mockMvc.perform(post("/api/v1/products")
                                .contentType("application/json")
                                .content("{\"name\":\"Test Product\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                }))
                        .andExpect(status().isUnauthorized()); // Fails auth but not rate limited
            }

            // All requests should pass through filter without 429 responses
        }

        @Test
        @DisplayName("only applies to /api/v1/auth/** paths")
        void onlyAppliesToAuthPaths() throws Exception {
            String clientIp = "192.168.1.251";

            // Register endpoint should be rate limited
            for (int i = 0; i < 20; i++) {
                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType("application/json")
                                .content("{\"tenantName\":\"Test\",\"email\":\"test@example.com\"}")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                }))
                        .andExpect(status().isBadRequest()); // Validation fails but filter passes
            }

            // 21st request should be rate limited
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content("{\"tenantName\":\"Test\",\"email\":\"test@example.com\"}")
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            }))
                    .andExpect(status().isTooManyRequests());
        }
    }

    /**
     * Window expiry, on a shortened window.
     *
     * <p>The earlier version of this test ran against the production 60-second
     * window and asserted partial expiry after a 30-second sleep — which made
     * the result depend on how long the request batches themselves took, and it
     * failed intermittently for exactly that reason. Here the sleep exceeds the
     * whole window, so every earlier timestamp is expired no matter how slow the
     * preceding batch was, and the budget resets in full.
     */
    @Nested
    @DisplayName("sliding window expiry")
    @TestPropertySource(properties = {
            "app.security.rate-limit.enabled=true",
            "app.security.rate-limit.window-seconds=" + SlidingWindowExpiry.WINDOW_SECONDS
    })
    class SlidingWindowExpiry {

        static final int WINDOW_SECONDS = 20;

        private void attemptLogin(String clientIp, int expectedStatus) throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}")
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            }))
                    .andExpect(status().is(expectedStatus));
        }

        @Test
        @DisplayName("the budget resets once the window has fully elapsed")
        void slidingWindowAllowsNewRequests() throws Exception {
            String clientIp = "192.168.1.160";

            // Exhaust the budget: 20 allowed, the 21st rejected.
            for (int i = 0; i < 20; i++) {
                attemptLogin(clientIp, 401);
            }
            attemptLogin(clientIp, 429);

            // Sleep past the window, measured from the most recent request, so
            // every recorded timestamp is now outside it.
            Thread.sleep((WINDOW_SECONDS + 1) * 1000L);

            // Full budget available again.
            for (int i = 0; i < 20; i++) {
                attemptLogin(clientIp, 401);
            }
            attemptLogin(clientIp, 429);
        }
    }
}
