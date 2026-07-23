package com.inventra.api.security;

import java.util.Set;

/** Shared request paths used by the backend security filter and authorization rules. */
public final class SecurityPaths {

    public static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private SecurityPaths() {
    }
}
