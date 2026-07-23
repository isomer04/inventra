+++
adr = "0004"
# This ADR documents a security practice (privilege separation) rather than a specific technology choice.
+++

# ADR-0004: Database User Privilege Separation

## Status
Accepted — implemented in `scripts/db-init.sh` and `docker-compose.prod.yml`

## Context
The application originally used a single DB user for Flyway migrations, application runtime, and operational backups, violating least privilege.

## Decision
Create explicit database identities during first-volume initialization:

1. **`inventra_flyway`** — Flyway migration user, granted `ALL PRIVILEGES` only on the application database. Used only during schema migration through `spring.flyway.user` and `spring.flyway.password`.

2. **`inventra_app`** — Application runtime user, granted only `SELECT, INSERT, UPDATE, DELETE` on the application database. Used by the Spring datasource.

3. **`inventra_backup`** — Operational backup user, granted only `SELECT, SHOW VIEW`. `mysqldump` uses `--single-transaction`, `--skip-lock-tables`, `--skip-triggers`, and `--no-tablespaces` so no broader global privilege is required. Dumps contain table definitions and row data but exclude triggers, routines, and events; migrations remain the source of those database objects.

The initializer is a shell script rather than raw SQL because the official MySQL image does not expand environment variables in `.sql` initialization files.

## Consequences
- **Gained:** Compromise of runtime or backup credentials does not grant DDL access.
- **Cost:** Three purpose-specific credentials must be generated, stored, and rotated.
- **Operational constraint:** Initialization runs only for a new MySQL data volume. Existing deployments must create/alter these users through a controlled migration before switching credentials.
- **Alternative:** A separate migration container remains a reasonable future option but is unnecessary at the current scale.
- **Reference:** https://cheatsheetseries.owasp.org/cheatsheets/Database_Security_Cheat_Sheet.html
