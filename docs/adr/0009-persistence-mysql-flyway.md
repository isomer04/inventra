+++
adr = "0009"

[[covers]]
id = "com.mysql:mysql-connector-j"
version = "9.2.0"
manifest = "backend/pom.xml"

[[covers]]
id = "org.springframework.boot:spring-boot-starter-flyway"
version = "4.0.6"
manifest = "backend/pom.xml"

[[covers]]
id = "org.flywaydb:flyway-mysql"
version = "11.2.0"
manifest = "backend/pom.xml"

[[covers]]
id = "mysql-database"
version = "8.4"
manifest = "docker-compose.yml"
+++

# ADR-0009: Persistence — MySQL 9 with Flyway Migrations

## Status
Accepted

## Context
Inventra requires a relational database for:
- **Multi-tenant data isolation** — each tenant's data must be logically or physically separated (see ADR-0003 for tenant isolation strategy)
- **Transactional consistency** — inventory adjustments, order creation, and audit log writes must be atomic
- **Schema evolution** — database schema changes must be versioned, repeatable, and auditable across development, staging, and production environments

MySQL 9 is a mature, widely-deployed RDBMS with strong ACID guarantees, row-level locking, and support for JSON columns. Flyway is a database migration tool that applies versioned SQL scripts in order, tracking applied migrations in a schema history table.

Cross-reference: ADR-0004 (DB privilege separation) defines the application user's restricted permissions (no DDL, no GRANT).

Source manifest: `backend/pom.xml`, `docker-compose.yml`

## Decision
Use **MySQL 9.2.0** as the relational database, with **Flyway 11.2.0** for schema migrations.

Dependencies:
- `com.mysql:mysql-connector-j` (JDBC driver)
- `spring-boot-starter-flyway` (Flyway integration)
- `org.flywaydb:flyway-mysql` (MySQL-specific Flyway dialect)

## Consequences

### Advantages
- **ACID transactions** — MySQL's InnoDB storage engine provides row-level locking and multi-version concurrency control (MVCC) for safe concurrent writes
- **Tenant isolation** — MySQL supports both schema-per-tenant and row-level tenant ID filtering (ADR-0003 chooses row-level with `tenant_id` column)
- **Versioned migrations** — Flyway applies SQL scripts in `src/main/resources/db/migration/` in order (V1__initial_schema.sql, V2__add_audit_table.sql, etc.), recording applied migrations in `flyway_schema_history` table
- **Repeatable migrations** — Flyway's checksum validation detects accidental changes to applied migrations, preventing schema drift
- **JSON support** — MySQL 9's JSON column type supports semi-structured data (e.g., order line items, product attributes) without requiring separate tables

### Disadvantages
- **MySQL-specific SQL** — Flyway migrations use MySQL syntax (e.g., `AUTO_INCREMENT`, `DATETIME(6)`), making database portability harder (acceptable for this project; no multi-database requirement)
- **Migration rollback** — Flyway Community Edition does not support automatic rollback; failed migrations must be manually fixed with new versioned scripts

### Mitigations
- Test Flyway migrations in local Docker environment before deploying to staging/production
- Use Flyway's `validate` command in CI to detect schema drift
- ADR-0004's privilege separation ensures the application user cannot accidentally run DDL; migrations run with a separate `flyway_user` account

### Alternatives considered
- **PostgreSQL** — Rejected. MySQL is more familiar to the team, and Inventra does not require PostgreSQL-specific features (advanced indexing, full-text search, PostGIS). MySQL's JSON support is sufficient.
- **Liquibase** — Rejected. Flyway's SQL-based migrations are simpler and more transparent than Liquibase's XML/YAML changesets. Flyway's checksum validation provides sufficient safety.
- **JPA schema generation (`spring.jpa.hibernate.ddl-auto`)** — Rejected. Auto-generated DDL is not version-controlled and cannot be reviewed before deployment. Flyway's explicit SQL scripts provide better auditability.

### References
- https://dev.mysql.com/doc/refman/9.2/en/
- https://flywaydb.org/documentation/
