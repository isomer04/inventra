package com.inventra.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sliding-window rate limiter for authentication endpoints.
 *
 * <p>Limits each client IP to 20 requests per sliding window
 * ({@code app.security.rate-limit.window-seconds}, default 60)
 * on any {@code /api/v1/auth/**} path,
 * including login, register, token refresh, and logout.
 * <p>Rate-limits both /login and /register endpoints.
 *
 * <p><b>Concurrency:</b> all deque mutations happen inside
 * {@link ConcurrentHashMap#compute} or {@link ConcurrentHashMap#computeIfPresent}
 * so the per-key bin lock guarantees atomicity. The {@link ArrayDeque} is never
 * accessed outside one of those callbacks.
 *
 * <p><b>Memory bounds:</b> {@link #evictStaleEntries()} runs on each auth
 * request and removes IPs whose window has fully expired. A hard cap of
 * 10,000 IPs prevents unbounded growth under IP-cycling attacks.
 *
 * <p><b>X-Forwarded-For trust:</b> only honoured when the
 * {@code TRUST_FORWARDED_FOR} environment variable is {@code true}. This is a
 * deliberately simple on/off switch — set it only when the application is
 * deployed behind a known reverse proxy that controls the header (e.g. nginx
 * in our docker-compose.prod.yml). Direct internet exposure with this flag
 * enabled allows clients to spoof their IP and bypass the rate limit.
 *
 * <p><b>Limitations:</b> in-memory and single-instance. Multi-instance
 * deployments must use a gateway-level rate limiter
 * ({@code limit_req_zone} in nginx, or a Redis-backed solution).
 */
@Slf4j
@Component
@Order(1)
public final class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Length of the sliding window. Configurable purely so the sliding-window
     * expiry test can use a window of seconds instead of sleeping through a
     * real minute — production leaves it at the default.
     */
    private final Duration window;

    public RateLimitFilter(
            @org.springframework.beans.factory.annotation.Value(
                    "${app.security.rate-limit.window-seconds:60}") long windowSeconds) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException(
                    "app.security.rate-limit.window-seconds must be greater than 0, was " + windowSeconds);
        }
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * On/off switch for the entire filter. Defaults to {@code true} in
     * production; integration tests set
     * {@code app.security.rate-limit.enabled=false} to avoid one slice of
     * tests poisoning the in-memory bucket for every other slice. The
     * dedicated {@code RateLimitTest} flips it back on via
     * {@code @SpringBootTest(properties = ...)}.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    /**
     * On/off switch for trusting the X-Forwarded-For header. Read once at
     * construction so toggling at runtime requires a restart (intentional).
     */
    private final boolean trustForwardedFor =
            "true".equalsIgnoreCase(System.getenv("TRUST_FORWARDED_FOR"));

    /** Per-IP sliding window of request timestamps. Mutated only inside compute callbacks. */
    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        if (enabled && request.getRequestURI().startsWith("/api/v1/auth/")) {
            String clientIp = resolveClientIp(request);
            evictStaleEntries();
            if (!isAllowed(clientIp)) {
                log.warn("Rate limit exceeded: ip={} path={}", clientIp, request.getRequestURI());
                response.setStatus(429);
                response.setContentType("application/json");
                long retryAfterSeconds = window.toSeconds();
                response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                response.getWriter().write(
                        "{\"status\":429,\"error\":\"Too Many Requests\"," +
                        "\"message\":\"Too many attempts. Please wait before trying again.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Visible for testing. Clears the per-IP sliding window. Dedicated
     * rate-limit tests call this between scenarios so accumulated state
     * from earlier scenarios does not bleed into later ones.
     */
    public void resetForTesting() {
        requestLog.clear();
    }

    /**
     * Sliding-window check. Atomic per IP via {@link ConcurrentHashMap#compute}.
     */
    private boolean isAllowed(String clientIp) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);
        AtomicBoolean allowed = new AtomicBoolean(false);

        requestLog.compute(clientIp, (ip, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            // Evict timestamps outside the current window
            while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
                deque.pollFirst();
            }
            // Enforce the threshold before recording so rejected requests do not
            // append unbounded timestamps once the limit is reached. Recording only
            // allowed requests bounds the deque to MAX_REQUESTS_PER_WINDOW while
            // preserving the original allow/deny outcome (a request is allowed iff
            // fewer than MAX_REQUESTS_PER_WINDOW timestamps are already in-window).
            if (deque.size() < RateLimitConstants.MAX_REQUESTS_PER_WINDOW) {
                deque.addLast(now);
                allowed.set(true);
            } else {
                allowed.set(false);
            }
            return deque;
        });

        return allowed.get();
    }

    /**
     * Removes IPs whose entire window has expired and enforces the
     * {@link RateLimitConstants#MAX_TRACKED_IPS} hard cap. All deque mutations happen inside
     * {@link ConcurrentHashMap#computeIfPresent} so they share the same bin
     * lock as {@link #isAllowed(String)} — there is no separate monitor.
     * Returning {@code null} from the lambda atomically removes the entry.
     */
    private void evictStaleEntries() {
        Instant windowStart = Instant.now().minus(window);

        Iterator<String> keys = requestLog.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            requestLog.computeIfPresent(key, (k, deque) -> {
                while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
                    deque.pollFirst();
                }
                return deque.isEmpty() ? null : deque;
            });
        }

        // Hard cap: drop oldest keys (insertion order via iterator) if still oversized
        if (requestLog.size() > RateLimitConstants.MAX_TRACKED_IPS) {
            int toRemove = requestLog.size() - RateLimitConstants.MAX_TRACKED_IPS;
            Iterator<String> capIter = requestLog.keySet().iterator();
            while (capIter.hasNext() && toRemove-- > 0) {
                capIter.next();
                capIter.remove();
            }
        }
    }

    /**
     * Resolves the client IP. Honours {@code X-Forwarded-For} only when
     * {@link #trustForwardedFor} is enabled (the {@code TRUST_FORWARDED_FOR}
     * env var is {@code true}). Without that flag, always uses the direct
     * connection address.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
