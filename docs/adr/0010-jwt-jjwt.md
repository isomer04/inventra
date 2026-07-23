+++
adr = "0010"

[[covers]]
id = "jjwt"
version = "0.13.0"
manifest = "backend/pom.xml"
+++

# ADR-0010: JWT Library — JJWT 0.13.0

## Status
Accepted

## Context
Inventra uses stateless JWT-based authentication for API requests. The backend must:
- **Generate JWTs** — issue signed tokens containing user ID, tenant ID, and roles after successful login
- **Validate JWTs** — verify signature, expiration, and issuer on every authenticated request
- **Parse claims** — extract user ID, tenant ID, and roles from validated tokens to populate Spring Security's `SecurityContext`

JJWT (Java JWT) is a pure-Java library for creating and parsing JWTs, with support for multiple signature algorithms (HMAC, RSA, ECDSA) and claim validation rules.

Source manifest: `backend/pom.xml`

## Decision
Use **JJWT 0.13.0** (three-artifact bundle: `jjwt-api`, `jjwt-impl`, `jjwt-jackson`).

The library is split into three artifacts:
- `jjwt-api` — public API (compile scope)
- `jjwt-impl` — implementation (runtime scope)
- `jjwt-jackson` — Jackson-based JSON serialization (runtime scope)

## Consequences

### Advantages
- **Type-safe API** — `Jwts.builder()` and `Jwts.parser()` provide fluent, compile-time-checked methods for token creation and validation
- **Automatic claim validation** — `requireIssuer()`, `requireExpiration()`, and custom claim validators prevent common JWT vulnerabilities (expired tokens, wrong issuer, missing claims)
- **Algorithm flexibility** — supports HMAC-SHA256 (symmetric), RSA (asymmetric), and ECDSA; Inventra uses HMAC-SHA256 with a 256-bit secret key stored in environment variables
- **Jackson integration** — `jjwt-jackson` uses the same Jackson ObjectMapper as Spring Boot, ensuring consistent JSON serialization for custom claims

### Disadvantages
- **Three-artifact dependency** — JJWT's modular design requires three separate Maven dependencies (acceptable; Spring Boot's BOM does not manage JJWT versions, so explicit version property is needed)
- **Secret key management** — HMAC-SHA256 requires a shared secret key; key rotation requires reissuing all active tokens (mitigated by short token expiration times: 15 minutes for access tokens, 7 days for refresh tokens)

### Mitigations
- Store JWT secret key in environment variable (`JWT_SECRET`), never in source code or application.properties
- Use short expiration times (15 minutes for access tokens) to limit exposure window if a token is compromised
- Implement refresh token rotation (new refresh token issued on each use) to detect token theft

### Alternatives considered
- **Auth0 java-jwt** — Rejected. Similar API to JJWT, but JJWT has better documentation and more active maintenance (last JJWT release: January 2025; last java-jwt release: November 2024).
- **Nimbus JOSE + JWT** — Rejected. More comprehensive (supports JWE, JWK, OAuth2 token introspection), but overkill for Inventra's use case (simple HMAC-signed JWTs). JJWT's simpler API reduces learning curve.
- **Spring Security OAuth2 Resource Server** — Rejected. Designed for OAuth2 / OpenID Connect flows with external authorization servers (Keycloak, Auth0). Inventra uses a custom JWT-based authentication system without OAuth2.

### References
- https://github.com/jwtk/jjwt
- https://github.com/jwtk/jjwt#install
