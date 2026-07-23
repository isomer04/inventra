# Decision Index

This file is the single source of truth for navigating all Architecture Decision Records (ADRs) in the Inventra project, grouped by area of concern.

## Backend

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0001](0001-sast-tooling-backend.md) | Add SpotBugs + Find Security Bugs to Backend Build | Accepted |
| [ADR-0005](0005-backend-platform-spring-boot.md) | Backend Platform — Spring Boot 4 on Java 25 | Accepted |
| [ADR-0006](0006-spring-boot-web-validation-starters.md) | Spring Boot Web and Validation Starters | Accepted |
| [ADR-0007](0007-spring-security-starter.md) | Spring Security Starter | Accepted |
| [ADR-0008](0008-spring-data-jpa-actuator.md) | Spring Data JPA and Actuator Starters | Accepted |
| [ADR-0009](0009-persistence-mysql-flyway.md) | Persistence — MySQL 9 with Flyway Migrations | Accepted |
| [ADR-0010](0010-jwt-jjwt.md) | JWT Library — JJWT 0.13.0 | Accepted |
| [ADR-0011](0011-lombok-mapstruct.md) | Developer Utilities — Lombok and MapStruct | Accepted |
| [ADR-0012](0012-springdoc-openapi.md) | API Documentation — SpringDoc OpenAPI 3 | Accepted |
| [ADR-0013](0013-backend-testing-spring-security-test.md) | Backend Testing — Spring Boot Test and Spring Security Test | Accepted |
| [ADR-0014](0014-testcontainers-h2.md) | Integration Testing — Testcontainers and H2 | Accepted |
| [ADR-0039](0039-commons-lang3-convergence-pin.md) | commons-lang3 Convergence Pin | Accepted |
| [ADR-0040](0040-refresh-token-client-storage.md) | Refresh Token Stored in `sessionStorage` | Accepted |

## Frontend

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0015](0015-frontend-framework-angular.md) | Frontend Framework — Angular 21 | Accepted |
| [ADR-0016](0016-ui-styling-bootstrap.md) | UI Styling — Bootstrap 5 with Icons | Accepted |
| [ADR-0017](0017-charting-chartjs-ng2charts.md) | Charting — Chart.js with ng2-charts | Accepted |
| [ADR-0018](0018-reactive-rxjs.md) | Reactive Programming — RxJS | Accepted |
| [ADR-0019](0019-frontend-build-tooling-angular-cli.md) | Frontend Build Tooling — Angular CLI | Accepted |
| [ADR-0020](0020-language-typescript.md) | Language — TypeScript | Accepted |
| [ADR-0021](0021-frontend-linting-eslint.md) | Frontend Linting — ESLint | Accepted |
| [ADR-0022](0022-code-formatting-prettier.md) | Code Formatting — Prettier | Accepted |
| [ADR-0023](0023-frontend-unit-testing-vitest.md) | Frontend Unit Testing — Vitest | Accepted |
| [ADR-0024](0024-frontend-component-testing-testing-library.md) | Frontend Component Testing — Testing Library | Accepted |
| [ADR-0025](0025-frontend-test-environment-jsdom.md) | Frontend Test Environment — jsdom | Accepted |
| [ADR-0026](0026-frontend-api-mocking-msw.md) | Frontend API Mocking — MSW | Accepted |
| [ADR-0027](0027-frontend-pbt-fast-check.md) | Frontend Property-Based Testing — fast-check | Accepted |
| [ADR-0028](0028-frontend-accessibility-testing-axe-core.md) | Frontend Accessibility Testing — axe-core | Accepted |

## Build / Quality / Infrastructure

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0002](0002-docker-image-digest-pinning.md) | Pin Docker Base Images to SHA-256 Digests | Accepted |
| [ADR-0003](0003-tenant-isolation-strategy.md) | Logical (Shared-DB) Tenant Isolation via tenant_id Column | Accepted |
| [ADR-0004](0004-db-user-privilege-separation.md) | Database User Privilege Separation (Flyway vs Runtime) | Accepted |
| [ADR-0029](0029-backend-build-tooling-maven.md) | Backend Build Tooling — Maven | Accepted |
| [ADR-0030](0030-frontend-package-management-npm.md) | Frontend Package Management — npm | Accepted |
| [ADR-0031](0031-owasp-dependency-check.md) | OWASP Dependency-Check | Accepted |
| [ADR-0032](0032-jacoco-code-coverage.md) | JaCoCo Code Coverage | Accepted |
| [ADR-0033](0033-pitest-mutation-testing.md) | PITest Mutation Testing | Accepted |
| [ADR-0034](0034-maven-enforcer.md) | Maven Enforcer Plugin | Accepted |
| [ADR-0035](0035-containerisation-docker-compose.md) | Containerisation — Docker and Docker Compose | Accepted |
| [ADR-0036](0036-nginx-reverse-proxy.md) | Nginx Reverse Proxy — Unprivileged Alpine Image | Accepted |
| [ADR-0037](0037-ci-platform-github-actions.md) | CI Platform — GitHub Actions | Accepted |
| [ADR-0038](0038-dependabot-dependency-automation.md) | Dependency Automation — Dependabot | Accepted |
