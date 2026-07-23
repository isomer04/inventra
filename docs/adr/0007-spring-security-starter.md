+++
adr = "0007"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-security"
version = "4.0.6"
manifest = "backend/pom.xml"
+++

# ADR-0007: Spring Security Starter

## Status
Accepted

## Context
Inventra is a multi-tenant SaaS application handling authentication, role-based access control (RBAC), and financial-adjacent data. The backend must enforce:
- JWT-based stateless authentication
- Tenant isolation (users can only access their own tenant's data)
- Role-based authorization (Admin, Manager, Staff roles with different permissions)
- Protection against common web vulnerabilities (CSRF, session fixation, clickjacking)

Spring Security provides a comprehensive security framework with filter-chain-based request interception, authentication providers, and authorization rules. The Spring Boot starter auto-configures security defaults and integrates with Spring MVC.

Cross-reference: ADR-0001 (SpotBugs + Find Security Bugs) provides SAST coverage for Spring Security misconfigurations.

Source manifest: `backend/pom.xml`

## Decision
Use **spring-boot-starter-security** (version resolved via Spring Boot BOM 4.0.6).

## Consequences

### Advantages
- **Filter-chain architecture** — security logic is applied before controller methods execute, ensuring consistent enforcement across all endpoints
- **JWT integration** — custom `OncePerRequestFilter` can extract and validate JWT tokens, then populate Spring Security's `SecurityContext` for downstream authorization checks
- **Method-level authorization** — `@PreAuthorize` annotations on service methods enforce role-based access control with SpEL expressions
- **CSRF protection** — enabled by default for state-changing operations (disabled for stateless JWT APIs via configuration)
- **Security headers** — X-Content-Type-Options, X-Frame-Options, and Strict-Transport-Security headers are auto-configured

### Disadvantages
- **Configuration complexity** — Spring Security's filter chain and authentication provider model has a steep learning curve; misconfiguration can lead to security bypasses (mitigated by ADR-0001's SAST tooling)
- **Default behavior** — Spring Security enables CSRF protection and form-based login by default, which must be explicitly disabled for stateless JWT APIs

### Mitigations
- Custom `SecurityFilterChain` bean explicitly disables CSRF and session management for stateless JWT authentication
- ADR-0001's SpotBugs + Find Security Bugs plugin detects common Spring Security misconfigurations (e.g., overly permissive `permitAll()` rules)

### Alternatives considered
- **Apache Shiro** — Rejected. Smaller ecosystem and less active maintenance compared to Spring Security. Spring Security's integration with Spring Boot and Spring Data JPA is superior.
- **Custom JWT filter without framework** — Rejected. Reinventing authentication and authorization logic is error-prone and time-consuming. Spring Security provides battle-tested implementations of common patterns.

### References
- https://spring.io/projects/spring-security
- https://docs.spring.io/spring-security/reference/
