# Changelog

All notable changes to Inventra are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Added
- **Test suite** — 208 tests across unit, integration, API boundary, and frontend layers. JaCoCo 0.8.14 for coverage reporting. PITest 1.25.1 for mutation testing on critical-tier services (OrderService, AuthService, StockMovementService, TenantService) with an 80% mutation score threshold.

---

## [1.0.0-rc.13] — CI/CD, ops hardening, and DB privilege separation (2026-05-24)

### Added
- GitHub Actions CI pipeline: backend build + test + SpotBugs SAST, OWASP Dependency-Check SCA (main branch only), frontend lint + production build, Docker image smoke test. All Actions pinned to commit SHAs.
- DB user privilege separation (ADR-0004): `scripts/db-init.sh` creates `inventra_flyway` (DDL), `inventra_app` (DML-only), and `inventra_backup` (read-only table-schema-and-row dump; no triggers, routines, or events) users on first MySQL startup.

### Changed
- Fix `.gitignore` — `docs/RUNBOOK.md` was excluded by `docs/*.md` glob; added `!docs/RUNBOOK.md` exception.

---

## [1.0.0-rc.12] — Frontend security fixes (2026-05-24)

### Security
- Fix open-redirect in `returnUrl` — `isSafeReturnUrl()` now rejects `//`, `/javascript`, `/data` prefixes before `navigateByUrl` is called.
- Align login form `minLength(6)` validator to backend `@Size(min=8)` — prevents a "valid" client state for passwords the API will reject.

### Changed
- Add `type="button"` to 4 buttons that were missing it and defaulting to `type="submit"`.

---

## [1.0.0-rc.11] — Privacy and compliance (2026-05-24)

### Added
- GDPR right-to-erasure: `DELETE /api/v1/tenant` pseudonymises all user/customer PII and suspends the tenant. Requires `confirmSlug` in the request body.
- `AuditLogRetentionService` — daily cron anonymises `actor_email` in audit records older than 365 days (configurable via `APP_AUDIT_RETENTION_DAYS`).
- `PRIVACY.md` and `TERMS.md` baseline documents.

---

## [1.0.0-rc.10] — Performance and capacity limits (2026-05-24)

### Added
- Composite index `(tenant_id, created_at)` on `stock_movement` — `V12__add_performance_indexes.sql`.
- Covering index `(order_id, to_status, changed_at)` on `order_status_history` — `V12`.
- Composite index `(tenant_id, parent_id)` on `category` — `V12`.

### Changed
- `spring.data.web.pageable.max-page-size: 200` — prevents `?size=100000` abuse.
- Report endpoints `/stock-movements` and `/order-summary` now require `startDate` + `endDate`; max range enforced at 366 days.

---

## [1.0.0-rc.9] — Resilience and scheduler hardening (2026-05-24)

### Added
- `SchedulerConfig` — replaces the single-thread default scheduler with a 2-thread `ThreadPoolTaskScheduler` with an explicit error handler.

### Changed
- Replace `OrderNumberGenerator` MAX+1 pattern with an atomic `order_sequence` table (`V11`) using `INSERT IGNORE + UPDATE + SELECT FOR UPDATE` — eliminates race condition on concurrent order creation.
- Wrap `RefreshTokenCleanupService.purgeExpiredTokens()` in try/catch — prevents an uncaught DB error from permanently killing the Spring scheduler thread.
- HikariCP pool tuning: `keepalive-time: 120s`, `max-lifetime: 25m`, `connection-timeout: 20s`.
- `server.tomcat.connection-timeout: 20000` — evicts slow/stalled clients.

---

## [1.0.0-rc.8] — Audit logging, N+1 fixes, and service hardening (2026-05-24)

### Security
- Add `AuditService`, `AuditLog` entity, `AuditLogRepository`, and `V10__create_audit_log.sql` — structured audit trail for user lifecycle, order transitions, and tenant registration.
- Add `@PreAuthorize` to `UserController.getById` and `update` — access policy now visible at the controller layer.
- Replace `TenantContext.getTenantId()` with `requireTenantId()` in `TenantController` — eliminates silent null propagation.

### Changed
- Add `JOIN FETCH` for `customer`, `createdBy`, `product`, `creator` on all list queries in `OrderRepository`, `StockMovementRepository`, `UserRepository` — eliminates N+1 lazy-load on list pages.
- Convert `ReportController` from field injection to constructor injection.
- Replace `DuplicateResourceException` with `ResourceInUseException` in `CategoryService.delete()` — correct semantic for referential-integrity blocks.

---

## [1.0.0-rc.7] — Data layer fixes and timestamp standardisation (2026-05-24)

### Added
- ADR-0004: DB user privilege separation decision record.

### Changed
- Add missing FK indexes via `V9__data_layer_fixes.sql`: `idx_user_tenant`, `idx_order_created_by`, `idx_order_history_changed_by`, `idx_stock_movement_created_by` — prevents full-table scans on FK JOIN columns.
- Standardise all timestamps to `Instant` (UTC-aware) across `StockMovement`, `InventoryItem`, `Customer` entities, their DTOs, and related repositories/services.
- Drop unused `order_status_enum` helper table via `V9`.
- Align `customer.status` to `ENUM('ACTIVE','INACTIVE')` via `V9` (was `VARCHAR(20)+CHECK`).

---

## [1.0.0-rc.6] — Input validation and output encoding (2026-05-24)

### Security
- Add `@Size(max)` constraints to all free-text fields across 7 DTOs, aligned to DB column lengths.
- Cap HTTP request body size at 2MB at both layers: `server.tomcat.max-swallow-size` and nginx `client_max_body_size`.
- Sanitise `IllegalArgumentException` handler — returns generic message instead of raw exception text. Introduce `InvalidRequestException` for developer-controlled user-facing messages.
- Add `JacksonConfig` with `HtmlCharacterEscapes` — Unicode-escapes `<`, `>`, `&`, `'` in all JSON output.

### Changed
- Add `@Min(-100000)` / `@Max(100000)` to `AdjustStockRequest.quantity`.

---

## [1.0.0-rc.5] — Authorization hardening (2026-05-23)

### Security
- Add explicit `@PreAuthorize` to 10 unannotated read endpoints across `CategoryController`, `InventoryController`, `OrderController`, and `ProductController`.
- Remove `ProductRepository.countByCategoryId(categoryId)` — unscoped cross-tenant method replaced everywhere by the scoped `countByCategoryIdAndTenantId`.
- Add `TenantContext.requireTenantId()` — throws with a clear message if called without a tenant context. Replace all 39 `getTenantId()` calls across 7 service files.

### Changed
- Add tenant contract Javadoc to `OrderItemRepository` and `OrderStatusHistoryRepository`.
- Add cross-tenant + GDPR contract Javadoc to `UserRepository.findByEmail`.

---

## [1.0.0-rc.4] — Authentication hardening (2026-05-23)

### Security
- Add `iss`/`aud` claims to JWT; enforce on parse.
- Add startup guard rejecting `CORS_ALLOWED_ORIGINS=*`.
- Add `@PostConstruct` JWT secret length validation.
- Add `TenantContext.isSet()` and defensive assertion in `RefreshTokenCleanupService`.
- Prepare TLS infrastructure: HTTPS server block (TLS 1.2/1.3) added to `nginx.conf` as commented blocks ready to activate.

### Changed
- Rewrite `AuthService.refresh()` — eliminate post-revoke read-back.
- Add `@Size(min=8)` to `LoginRequest.password`.

---

## [1.0.0-rc.3] — Dependency and supply chain updates (2026-05-23)

### Security
- Resolve `brace-expansion` CVE (GHSA-jxxr-4gwj-5jf2) via npm `overrides`.
- Resolve `qs` CVE (GHSA-q8mj-m7cp-5q26) via `npm audit fix`.

### Changed
- Upgrade `dependency-check-maven` 10.0.4 → 12.2.2 (NVD API v2 required for current CVE data).
- Upgrade `spotbugs-maven-plugin` 4.8.6.4 → 4.9.8.3, `findsecbugs-plugin` 1.13.0 → 1.14.0.
- Upgrade `jjwt` 0.12.6 → 0.13.0.
- Pin `commons-lang3` to 3.20.0 in `dependencyManagement` to satisfy `requireUpperBoundDeps`.
- Add Dependabot ignore rules for non-LTS Java and Node versions.

---

## [1.0.0-rc.2] — Repo hygiene and GitHub configuration (2026-05-23)

### Added
- `SECURITY.md` — private vulnerability disclosure path, response timeline, and scope.
- `LICENSE`, `CONTRIBUTING.md`, root `.gitattributes`.
- `.github/PULL_REQUEST_TEMPLATE.md`, issue templates (bug, feature), `CODEOWNERS`, `dependabot.yml`.

### Changed
- Update README project structure tree and add Architecture & Decisions section with ADR table.
- Document Conventional Commits standard in `CONTRIBUTING.md`.

---

## [1.0.0-rc.1] — SAST, image pinning, and baseline fixes (2026-05-23)

### Security
- Add SpotBugs + Find Security Bugs to Maven build (bound to `verify`, fails on Medium+).
- Remove plaintext demo password from `V6__seed_demo_user.sql` SQL comment.
- Add `preload` to `Strict-Transport-Security` nginx header.

### Added
- `backend/spotbugs-exclude.xml` — exclusion filter for test classes with justification comments.
- ADR-0001 (SpotBugs decision) and ADR-0002 (image digest pinning decision).
- Root `.editorconfig`.

### Changed
- Pin all Docker base images to SHA-256 digests.
- Upgrade `no-explicit-any` ESLint rule from `warn` to `error`; replace all 5 `any` usages with proper types.
- Add Maven Enforcer plugin (`requireUpperBoundDeps`, Maven 3.9+, Java 25+).

---

## [1.0.0] — Initial release (pre-launch)

### Added
- Multi-tenant architecture with tenant isolation
- JWT authentication with refresh-token rotation
- Role-based access control (ADMIN, MANAGER, WAREHOUSE_STAFF, VIEWER)
- User management
- Hierarchical category management
- Product catalog with search, filtering, and pagination
- Inventory management with stock movements (receipt, adjustment, reserve, release, ship)
- Customer management
- Order lifecycle with status history
- Reporting endpoints (inventory summary, stock movements, order summary, top products)
- Rate limiting on auth endpoints
- Angular 21 SPA
- Production Docker Compose with nginx reverse proxy and security headers
- Flyway database migrations (V1–V8)
- Backup and restore scripts
