+++
adr = "0031"

[[covers]]
id = "org.owasp:dependency-check-maven"
version = "${dependency-check-maven.version}"
manifest = "backend/pom.xml"
+++

# ADR-0031: Dependency Vulnerability Scanning — OWASP Dependency-Check

## Status
Accepted

## Context
Inventra is a multi-tenant SaaS application handling authentication, authorization, and business-critical inventory and order data. The backend depends on dozens of third-party libraries (Spring Boot, JWT, database drivers, testing frameworks) that may contain known security vulnerabilities (CVEs). A vulnerability in a transitive dependency can expose the entire application to attack, even if the direct dependencies are carefully chosen.

The National Vulnerability Database (NVD) maintains a comprehensive catalogue of CVEs, but manually checking every dependency against the NVD on every build is impractical. An automated Software Composition Analysis (SCA) tool is required to scan the dependency tree, match artifacts against the NVD, and fail the build when high-severity vulnerabilities are detected.

The backend build declares `org.owasp:dependency-check-maven` version `12.2.2` in `backend/pom.xml`. This version uses the NVD API v2, which is the current supported API; the older v1 API was deprecated and no longer returns data, meaning older scanner versions (10.x and earlier) would silently produce empty results.

## Decision
Use `org.owasp:dependency-check-maven` version `12.2.2` as the backend's dependency vulnerability scanner, bound to the Maven `verify` phase via the `security-scan` profile.

Source manifest: `backend/pom.xml`

The plugin is configured to:
- Fail the build for any vulnerability with CVSS score ≥ 7 (High or Critical severity)
- Skip test-scoped dependencies (test libraries are not deployed to production)
- Load suppression rules from `owasp-suppressions.xml` for confirmed false positives
- Run on-demand via `mvn -P security-scan verify` or in CI when the `security-scan` profile is activated

OWASP Dependency-Check is the de facto standard open-source SCA tool for Maven projects. It integrates directly into the Maven build lifecycle, requires no separate server infrastructure, and provides actionable CVE reports with CVSS scores and remediation guidance.

## Consequences

**Advantages:**
- **Automated CVE detection** on every CI build (when the `security-scan` profile is active) catches vulnerable dependencies before they reach production, reducing the window of exposure for known exploits.
- **NVD API v2 support** ensures the scanner continues to receive up-to-date vulnerability data; version 12.2.2 is compatible with the current NVD API and will not silently fail like older versions.
- **Build-time enforcement** via `failBuildOnCVSS=7` prevents merging pull requests that introduce high-severity vulnerabilities, making security a mandatory quality gate rather than an optional post-deployment audit.
- **Suppression file support** allows the team to document and suppress confirmed false positives (e.g., a CVE that applies only to a usage pattern Inventra does not exercise) without disabling the entire check, keeping the signal-to-noise ratio high.

**Disadvantages / costs / risks:**
- **Scan time overhead**: The first run of Dependency-Check downloads the NVD database (several hundred MB) and builds a local index, adding 2–5 minutes to the build. Subsequent runs are faster (~30–60 seconds) because the database is cached, but the initial cold-start penalty is significant.
  - *Mitigation (applied)*: The plugin is bound to a Maven profile (`security-scan`) rather than the default build lifecycle, so developers can run `mvn verify` locally without triggering the scan. The scan runs in CI on every push, where the NVD database cache is persisted across builds.
- **False positives**: The NVD database sometimes flags vulnerabilities that do not apply to Inventra's usage of a library (e.g., a CVE affecting a server-side feature of a library when Inventra only uses the client-side API). Each false positive requires manual triage and a suppression entry.
  - *Mitigation (applied)*: The `owasp-suppressions.xml` file documents each suppression with a dated comment explaining why the CVE does not apply. Suppressions are reviewed during dependency upgrades to ensure they remain valid.
- **Transitive dependency noise**: The scanner reports vulnerabilities in transitive dependencies that Inventra does not directly control. Fixing these often requires waiting for an upstream library to release a patched version, or manually excluding the vulnerable transitive dependency and replacing it with a safe version.
  - *Mitigation (planned)*: The Maven Enforcer plugin (see ADR-0034) enforces `requireUpperBoundDeps`, which surfaces version conflicts early and makes it easier to pin a safe version of a transitive dependency in the `<dependencyManagement>` section.

### Alternatives considered
- **Snyk** — A commercial SCA platform with a free tier for open-source projects. Snyk provides a richer vulnerability database (including proprietary research beyond the NVD), automated pull requests to fix vulnerabilities, and a web dashboard for tracking remediation progress. Rejected because Snyk requires creating an account, granting the service read access to the repository, and integrating a third-party CI step. For a solo project, the operational overhead and external dependency outweigh the benefits of the enhanced database. OWASP Dependency-Check's NVD-only coverage is sufficient for Inventra's threat model, and the tool runs entirely within the existing Maven build without external service dependencies.
- **GitHub Dependabot Security Alerts** — GitHub's built-in vulnerability scanner, which automatically opens pull requests to upgrade vulnerable dependencies. Rejected as a *replacement* for Dependency-Check because Dependabot only scans direct dependencies declared in `pom.xml` and `package.json`, not transitive dependencies. Dependabot is used *in addition to* Dependency-Check (see ADR-0038) to automate dependency upgrades, but it does not provide the same depth of transitive-dependency scanning or build-time enforcement that Dependency-Check offers.
- **OWASP Dependency-Check 10.x or earlier** — Older versions of the plugin that use the deprecated NVD API v1. Rejected because the v1 API was shut down in 2023, and these versions now silently produce empty scan results, giving a false sense of security. Version 12.2.2 is the minimum version that supports NVD API v2.

### References
- <https://jeremylong.github.io/DependencyCheck/>
- <https://jeremylong.github.io/DependencyCheck/dependency-check-maven/>
- <https://nvd.nist.gov/>
