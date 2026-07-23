+++
adr = "0034"

[[covers]]
id = "org.apache.maven.plugins:maven-enforcer-plugin"
version = "${maven-enforcer-plugin.version}"
manifest = "backend/pom.xml"
+++

# ADR-0034: Maven Enforcer Plugin for Build Reproducibility

## Status
Accepted

## Context
Inventra's backend build declares 40+ direct and transitive dependencies across Spring Boot, Spring Security, Spring Data JPA, JJWT, Flyway, Testcontainers, and other libraries. Maven's dependency resolution algorithm selects versions based on proximity and declaration order, which can silently downgrade a direct dependency when a transitive dependency declares an older version. This creates non-deterministic builds where the same `pom.xml` can resolve different versions depending on the order dependencies are processed.

Additionally, the project requires Java 25 (for virtual threads and modern language features) and Maven 3.9+ (for improved dependency resolution and reproducible builds). Without enforcement, developers or CI environments running older Java or Maven versions would produce subtly different artifacts or encounter runtime failures that don't reproduce locally.

The `backend/pom.xml` declares `maven-enforcer-plugin` version `3.6.3` (source: `backend/pom.xml` property `<maven-enforcer-plugin.version>3.6.3</maven-enforcer-plugin.version>`). The plugin is bound to the `validate` phase and runs on every build.

## Decision
Use `org.apache.maven.plugins:maven-enforcer-plugin` version `3.6.3` bound to the `validate` phase with three rules:

1. **`requireUpperBoundDeps`** — fails the build if a transitive dependency would silently downgrade a direct dependency. This caught the `commons-lang3` convergence issue where Spring Boot BOM brought `3.20.0` but SpringDoc's `swagger-core-jakarta` declared `3.19.0`. The fix was to add an explicit `<dependencyManagement>` pin to `3.20.0`.

2. **`requireMavenVersion [3.9,)`** — fails the build if Maven is older than 3.9. Maven 3.9 introduced reproducible build timestamps and improved dependency resolution; older versions produce non-reproducible artifacts.

3. **`requireJavaVersion [25,)`** — fails the build if Java is older than 25. Spring Boot 4.0.x targets Java 25 as its primary runtime, and Inventra's code uses Java 25 language features (record patterns, sealed classes). Running on an older JDK would produce compile errors or runtime failures.

The plugin runs in the `validate` phase (before `compile`), so version mismatches are caught immediately rather than after a long build.

## Consequences

**Advantages:**
- **Reproducible builds** — `requireUpperBoundDeps` ensures that the same `pom.xml` always resolves the same dependency versions, regardless of Maven's internal resolution order. This eliminates "works on my machine" issues caused by transitive version conflicts.
- **Early failure on environment mismatch** — developers or CI runners using the wrong Java or Maven version get an immediate, actionable error message rather than a cryptic runtime failure or silent misbehavior.
- **Dependency convergence visibility** — when a new dependency introduces a version conflict, the build fails with a clear message showing which transitive dependency is causing the downgrade. This was critical during the JJWT 0.13.0 upgrade, where the `commons-lang3` conflict surfaced immediately.
- **Zero runtime cost** — the plugin runs only during the build; it has no impact on the compiled artifact or runtime behavior.

**Disadvantages / costs / risks:**
- **Build-time strictness** — the `requireUpperBoundDeps` rule can fail the build when adding a new dependency that transitively pulls an older version of an existing dependency. This requires manual intervention (adding a `<dependencyManagement>` pin) rather than silently accepting the downgrade.
  - *Mitigation (applied)*: The `backend/pom.xml` already includes a `<dependencyManagement>` section with explicit pins for known convergence issues (e.g. `commons-lang3 3.20.0`). When a new conflict arises, the error message shows exactly which dependency to pin.
- **Developer environment constraints** — requiring Java 25 and Maven 3.9+ means developers must upgrade their local toolchain. This is acceptable because Spring Boot 4.0.x requires Java 25 anyway, and Maven 3.9 is the current stable release.
  - *Mitigation (applied)*: The `README.md` documents the required Java and Maven versions. The CI environment (GitHub Actions) uses `actions/setup-java@v4` with `java-version: 25` and `maven-version: 3.9.x` to ensure consistency.
- **False positives on test-scoped dependencies** — `requireUpperBoundDeps` checks all scopes by default, including `test`. If a test-scoped dependency has a version conflict, the build fails even though it doesn't affect the production artifact.
  - *Mitigation (applied)*: The rule is configured without scope exclusions because test dependencies (Testcontainers, H2, JUnit) are part of the build's correctness guarantee. A version mismatch in test scope could cause flaky tests or false negatives.

### Alternatives considered
- **No enforcer plugin** (rejected — without `requireUpperBoundDeps`, the `commons-lang3` convergence issue would have gone undetected until runtime, potentially causing subtle bugs or ClassLoader conflicts in production. The plugin's build-time cost is negligible compared to the risk of non-reproducible builds.)
- **Maven Versions Plugin** (rejected — the Versions Plugin can *report* dependency updates and convergence issues, but it doesn't *fail* the build. The Enforcer Plugin's fail-fast behavior is critical for CI/CD pipelines where a passing build must guarantee reproducibility.)
- **Gradle with dependency locking** (rejected — Gradle's dependency locking feature provides similar reproducibility guarantees, but migrating from Maven to Gradle would require rewriting the entire build configuration and re-validating all plugins (SpotBugs, OWASP Dependency-Check, JaCoCo, PITest). The Maven Enforcer Plugin achieves the same goal with zero migration cost.)

### References
- <https://maven.apache.org/enforcer/maven-enforcer-plugin/>
- <https://maven.apache.org/enforcer/enforcer-rules/requireUpperBoundDeps.html>
- <https://maven.apache.org/enforcer/enforcer-rules/requireMavenVersion.html>
- <https://maven.apache.org/enforcer/enforcer-rules/requireJavaVersion.html>
