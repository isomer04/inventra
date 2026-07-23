+++
adr = "0014"

[[covers]]
id = "testcontainers"
version = "1.21.4"
manifest = "backend/pom.xml"

[[covers]]
id = "com.h2database:h2"
version = "2.3.232"
manifest = "backend/pom.xml"
+++

# ADR-0014: Integration Testing — Testcontainers and H2

## Status
Accepted

## Context
Inventra's backend requires integration tests that verify:
- **Database interactions** — JPA repositories, Flyway migrations, and SQL queries work correctly against a real database
- **Multi-tenant isolation** — tenant ID filtering prevents cross-tenant data leaks
- **Transaction behavior** — rollback on exceptions, optimistic locking, and constraint violations

Integration tests should use a real database (MySQL) to match production behavior, but must not require manual database setup or leave test data behind.

Testcontainers provides Docker-based test fixtures that start a MySQL container for each test run, then tear it down automatically. H2 is an in-memory database used for fast unit tests that do not require MySQL-specific features.

Source manifest: `backend/pom.xml`

## Decision
Use **Testcontainers 2.0.5** with **MySQL module 1.21.4** for integration tests, and **H2 2.3.232** for fast unit tests.

## Consequences

### Advantages
- **Real database** — Testcontainers runs MySQL in Docker, ensuring integration tests use the same database engine as production (no H2 vs. MySQL dialect mismatches)
- **Automatic cleanup** — Testcontainers stops and removes the MySQL container after tests complete, preventing test data pollution
- **Flyway migration testing** — integration tests can verify Flyway migrations apply correctly and produce the expected schema
- **Fast unit tests** — H2 in-memory database provides sub-second startup for unit tests that do not require MySQL-specific features (e.g., JSON columns, full-text search)

### Disadvantages
- **Docker dependency** — Testcontainers requires Docker to be installed and running on developer machines and CI servers (acceptable; Docker is already required for local development)
- **Slower integration tests** — starting a MySQL container adds 5–10 seconds to test startup time (mitigated by running integration tests separately from unit tests)
- **H2 dialect differences** — H2's SQL dialect differs from MySQL (e.g., `AUTO_INCREMENT` vs. `IDENTITY`), so H2-based unit tests may not catch MySQL-specific issues

### Mitigations
- Use Testcontainers for integration tests that require MySQL-specific features (JSON columns, Flyway migrations, tenant isolation)
- Use H2 for fast unit tests that only exercise JPA repository methods without complex SQL
- CI pipeline runs integration tests in a separate job with Docker available

### Alternatives considered
- **Embedded MySQL (MariaDB4j)** — Rejected. MariaDB4j is no longer actively maintained and does not support MySQL 9. Testcontainers provides better MySQL version compatibility.
- **H2 only** — Rejected. H2's SQL dialect differs from MySQL, leading to false positives (tests pass with H2 but fail with MySQL in production). Testcontainers ensures integration tests use the same database engine as production.
- **Manual MySQL setup** — Rejected. Requiring developers to manually install and configure MySQL increases onboarding friction and risks test data pollution. Testcontainers provides automatic setup and teardown.

### References
- https://testcontainers.com/
- https://www.h2database.com/
