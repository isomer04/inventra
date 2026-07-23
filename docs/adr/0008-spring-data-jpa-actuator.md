+++
adr = "0008"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-data-jpa"
version = "4.0.6"
manifest = "backend/pom.xml"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-actuator"
version = "4.0.6"
manifest = "backend/pom.xml"
+++

# ADR-0008: Spring Data JPA and Actuator Starters

## Status
Accepted

## Context
Inventra's backend requires:
- **Data access layer** — CRUD operations for tenants, users, inventory items, orders, and audit logs, with support for complex queries (e.g., "find all orders for tenant X with status Y")
- **Operational observability** — health checks, metrics, and audit event logging for production monitoring

Spring Data JPA provides repository abstractions that eliminate boilerplate JDBC code, while Spring Boot Actuator exposes production-ready endpoints for health, metrics, and application info.

Source manifest: `backend/pom.xml`

## Decision
Use **spring-boot-starter-data-jpa** and **spring-boot-starter-actuator** (versions resolved via Spring Boot BOM 4.0.6).

## Consequences

### Advantages
- **Repository pattern** — `JpaRepository<Entity, ID>` interfaces provide CRUD methods without implementation code; custom queries use `@Query` annotations or method name conventions
- **Transaction management** — `@Transactional` annotations automatically handle transaction boundaries, rollback on exceptions, and connection pooling
- **Lazy loading** — JPA entity relationships (`@OneToMany`, `@ManyToOne`) support lazy fetching to avoid N+1 query problems
- **Health checks** — Actuator's `/actuator/health` endpoint reports database connectivity, disk space, and custom health indicators
- **Metrics** — Actuator integrates with Micrometer to expose JVM metrics, HTTP request counts, and database connection pool stats (compatible with Prometheus, Grafana)

### Disadvantages
- **N+1 query risk** — lazy loading can trigger unintended database queries if not carefully managed (mitigated by `@EntityGraph` or explicit `JOIN FETCH` queries)
- **Actuator security** — `/actuator` endpoints expose sensitive information (heap dumps, environment variables) and must be secured or disabled in production (mitigated by Spring Security configuration)

### Mitigations
- Use `@EntityGraph` or `JOIN FETCH` for queries that need related entities to avoid N+1 problems
- Secure `/actuator` endpoints with Spring Security rules (require `ADMIN` role or restrict to internal network)

### Alternatives considered
- **MyBatis** — Rejected. Requires manual SQL and result mapping, which increases boilerplate compared to Spring Data JPA's repository pattern. Better suited for legacy databases with complex stored procedures.
- **jOOQ** — Rejected. Type-safe SQL DSL is appealing, but Spring Data JPA's repository pattern provides faster development for standard CRUD operations. jOOQ is better for complex reporting queries (not Inventra's primary use case).
- **Micrometer without Actuator** — Rejected. Actuator provides pre-built health checks and metrics endpoints with zero configuration; using Micrometer alone requires manual endpoint implementation.

### References
- https://spring.io/projects/spring-data-jpa
- https://docs.spring.io/spring-boot/reference/actuator/
