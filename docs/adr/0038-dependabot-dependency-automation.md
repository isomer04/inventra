+++
adr = "0038"

[[covers]]
id = "prettier"
version = "^3.8.1"
manifest = "frontend/package.json"
+++

# ADR-0038: Dependency Automation — Dependabot

## Status
Accepted

## Context
Inventra uses multiple dependency ecosystems:
- **Maven** — backend Java dependencies (Spring Boot, JJWT, Testcontainers, etc.)
- **npm** — frontend JavaScript dependencies (Angular, Vitest, ESLint, etc.)
- **GitHub Actions** — CI workflow actions (checkout, setup-java, setup-node, etc.)
- **Docker** — base images (eclipse-temurin, nginx-unprivileged, etc.)

Dependencies must be kept up-to-date to:
- **Patch security vulnerabilities** — CVEs in transitive dependencies (e.g., Log4j, Spring Framework)
- **Adopt bug fixes** — upstream bug fixes in libraries and frameworks
- **Maintain compatibility** — avoid falling too far behind upstream versions, which makes future upgrades harder

Dependabot is GitHub's built-in dependency update service that automatically opens pull requests when new versions are available.

Source manifest: `.github/dependabot.yml`

## Decision
Use **Dependabot v2** to automate dependency updates for Maven, npm, GitHub Actions, and Docker ecosystems.

Configuration:
- **Maven** — weekly updates, grouped by update type (security vs. non-security)
- **npm** — weekly updates, grouped by update type
- **GitHub Actions** — weekly updates, all actions pinned to commit SHAs
- **Docker** — weekly updates for base images in Dockerfiles

## Consequences

### Advantages
- **Automated security patches** — Dependabot opens PRs for security vulnerabilities within hours of disclosure, reducing exposure window
- **Reduced manual work** — no need to manually check for dependency updates; Dependabot monitors all ecosystems
- **PR-based workflow** — each update is a separate PR with changelog links, allowing review and testing before merge
- **Compatibility testing** — CI runs on every Dependabot PR, catching breaking changes before merge

### Disadvantages
- **PR noise** — Dependabot can open many PRs per week (10–20 for a project with 50+ dependencies), requiring triage and review
- **Breaking changes** — major version updates may introduce breaking changes that require code changes (e.g., Spring Boot 3 → 4, Angular 17 → 18)
- **Transitive dependency conflicts** — updating one dependency may cause version conflicts with transitive dependencies (mitigated by Maven Enforcer's `requireUpperBoundDeps` rule)

### Mitigations
- Group related updates (e.g., all `@angular/*` packages) into a single PR to reduce noise
- Use Dependabot's `ignore` configuration to skip major version updates for dependencies with known breaking changes
- CI runs on every Dependabot PR, catching build failures and test regressions before merge

### Alternatives considered
- **Renovate** — Rejected. Renovate provides more configuration options (e.g., auto-merge rules, custom grouping), but Dependabot is built into GitHub and requires no additional setup.
- **Manual updates** — Rejected. Manual dependency updates are error-prone and time-consuming; Dependabot automates the process with minimal configuration.
- **Snyk** — Rejected. Snyk provides vulnerability scanning and automated PRs, but requires a separate account and paid plan for private repositories. Dependabot is free for all GitHub repositories.

### References
- https://docs.github.com/en/code-security/dependabot
- https://github.com/dependabot
