+++
adr = "0003"
# This ADR documents an architectural pattern (tenant isolation strategy) rather than a specific technology choice.
+++

# ADR-0003: Logical (Shared-DB) Tenant Isolation via `tenant_id` Column

## Status
Accepted

## Context
Inventra is a multi-tenant SaaS. At the data layer, there are three common isolation models:
1. **Separate databases per tenant** — strongest isolation, highest operational cost.
2. **Separate schemas per tenant** — moderate isolation, moderate cost.
3. **Shared database, shared schema with `tenant_id` column** — weakest isolation, lowest cost.

The current implementation uses option 3: every tenanted table has a `tenant_id` column, and all queries are expected to filter by `tenant_id` sourced from the JWT claim via `TenantContext`.

## Decision
Accept shared-database isolation as the architecture for this stage of the project. The `tenant_id` column is present on all tenanted entities and repositories must filter by it.

The **risk** accepted: a single missing `WHERE tenant_id = ?` clause in any repository method constitutes a full data breach across all tenants. This risk must be mitigated by:
1. An audit of all repository queries to verify every method filters by `tenant_id`.
2. Integration tests that assert cross-tenant data is unreachable per endpoint.
3. Database-level enforcement considered as a future control (views, Row Security Policies — MySQL 8 does not support RLS natively, so application-level enforcement is the only option short of schema-per-tenant).

## Consequences
- **Gained:** Simple schema, single Flyway migration path, low operational overhead.
- **Risk accepted:** Application-level enforcement only — no database-level guard. A coding mistake can leak all tenant data.
- **Mitigated by:** Audit of all repository queries for `tenant_id` filtering; `requireTenantId()` guard that throws loudly if called without a tenant context; integration tests asserting cross-tenant data is unreachable.
- **Alternatives considered:**
  - Schema-per-tenant (rejected — adds complexity to Flyway migrations and connection pooling without clear benefit at current scale).
  - Separate databases (rejected — operationally expensive for a single-instance deployment).
- **Reference:** https://learn.microsoft.com/en-us/azure/architecture/guide/multitenant/considerations/tenancy-models


