+++
adr = "0005"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-web"
version = "4.0.6"
manifest = "backend/pom.xml"
+++

# ADR-0005: Backend Platform — Spring Boot 4 on Java 25

## Status
Accepted

## Context
Inventra is a multi-tenant SaaS inventory and order management system requiring robust authentication, RBAC, database access, REST API endpoints, and operational observability. The backend must support rapid feature development while maintaining security and reliability standards appropriate for financial-adjacent data handling.

Spring Boot provides an opinionated, production-ready framework with auto-configuration for web, security, data access, and monitoring concerns. Version 4.0.6 (released March 2025) runs on Java 25 and includes Spring Framework 7.x with enhanced virtual thread support and improved startup performance.

Source manifest: `backend/pom.xml`

## Decision
Use **Spring Boot 4.0.6** as the backend application framework, running on **Java 25**.

The parent POM (`org.springframework.boot:spring-boot-starter-parent:4.0.6`) provides dependency management, plugin configuration, and sensible defaults for all Spring Boot starters.

## Consequences

### Advantages
- **Opinionated auto-configuration** reduces boilerplate for common patterns (web controllers, JPA repositories, security filters)
- **Large ecosystem** of starters and community libraries for authentication (Spring Security), API documentation (SpringDoc), database migrations (Flyway), and testing (Spring Boot Test)
- **Production-ready features** via Spring Boot Actuator (health checks, metrics, audit events) with minimal configuration
- **Virtual thread support** in Spring Framework 7.x improves throughput for I/O-bound operations (database queries, external API calls) without code changes
- **Active maintenance** — Spring Boot 4.x is the current major version with long-term support

### Disadvantages
- **Startup time** — Spring Boot's classpath scanning and bean initialization add 2–4 seconds to application startup (acceptable for server-side deployment, but slower than Quarkus or Micronaut in native-image mode)
- **Memory footprint** — baseline heap usage is ~150–200 MB for a minimal Spring Boot application (higher than lightweight frameworks)
- **Framework lock-in** — heavy use of Spring-specific annotations and auto-configuration makes migration to other frameworks costly

### Mitigations
- Startup time is not a concern for long-running server processes; container orchestration (Kubernetes) handles restarts
- Memory footprint is acceptable for modern cloud instances (2 GB+ RAM standard)
- Framework lock-in is mitigated by using standard Java EE / Jakarta EE APIs (JPA, Bean Validation, Servlet API) where possible, keeping Spring-specific code in configuration classes

### Alternatives considered
- **Quarkus** — Rejected. Faster startup and lower memory usage in native-image mode, but smaller ecosystem and less mature tooling for Spring Security equivalents. Native compilation adds build complexity and debugging friction.
- **Micronaut** — Rejected. Similar benefits to Quarkus (compile-time DI, fast startup), but Spring Boot's larger community and existing team familiarity outweigh the performance gains for this project's scale.
- **Plain Jakarta EE (WildFly / Payara)** — Rejected. Requires more manual configuration for common patterns (REST exception handling, security filter chains, database connection pooling). Spring Boot's auto-configuration provides better developer experience.

### References
- https://spring.io/projects/spring-boot
- https://docs.spring.io/spring-boot/reference/
