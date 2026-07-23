package com.inventra.api.security;

import com.inventra.api.entity.TenantStatus;
import com.inventra.api.repository.UserRepository;
import com.inventra.api.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * Skip token enforcement on the unauthenticated auth endpoints.
     *
     * <p>These are {@code permitAll} in {@link SecurityConfig}, but this filter 401s on any
     * request carrying an invalid token regardless of the path. That locked clients out of
     * {@code /auth/refresh} — the one endpoint able to fix a stale token — whenever they
     * sent the expired access token along with the refresh request.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && SecurityPaths.PUBLIC_AUTH_ENDPOINTS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractBearerToken(request);
            if (token != null) {
                if (!jwtService.isTokenValid(token)) {
                    // Token present but invalid/expired — return 401 so the frontend
                    // auth interceptor can attempt a refresh rather than silently
                    // leaving the security context empty and getting a confusing 403.
                    sendUnauthorized(response, "Token expired or invalid");
                    return;
                }

                String userId = jwtService.extractUserId(token);
                String tenantId = jwtService.extractTenantId(token);

                var userOpt = userRepository.findByIdWithTenant(userId);
                if (userOpt.isEmpty()) {
                    sendUnauthorized(response, "User not found");
                    return;
                }

                var user = userOpt.get();
                if (user.getTenant() == null || !tenantId.equals(user.getTenant().getId())) {
                    sendUnauthorized(response, "Tenant mismatch");
                    return;
                }

                // Access tokens are self-contained and live for 15 minutes, so revocation
                // has to happen here or not at all. Building the Authentication directly
                // bypasses DaoAuthenticationProvider's account-status checks, which is what
                // would normally consult User.isEnabled(). Without these two checks a
                // deactivated user, or a user in a suspended/erased tenant, keeps full API
                // access until their current token expires.
                if (!user.isEnabled() || user.getTenant().getStatus() != TenantStatus.ACTIVE) {
                    sendUnauthorized(response, "Account is inactive");
                    return;
                }

                TenantContext.setTenantId(tenantId);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Sanitize message to prevent XSS/CRLF injection in the response body.
        // Only allow alphanumeric characters, spaces, and basic punctuation.
        String safeMessage = message == null ? "Unauthorized"
                : message.replaceAll("[^a-zA-Z0-9 .,_\\-]", "");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + safeMessage + "\"}");
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
