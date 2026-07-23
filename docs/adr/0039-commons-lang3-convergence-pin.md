+++
adr = "0039"

[[covers]]
id = "org.apache.commons:commons-lang3"
version = "3.20.0"
manifest = "backend/pom.xml"
+++

# ADR-0039: commons-lang3 Convergence Pin

## Status
Accepted

## Context
Inventra's backend pulls in `org.apache.commons:commons-lang3` transitively from two independent dependency trees:

- **Spring Boot BOM** (`spring-boot-starter-parent` 4.0.6) manages `commons-lang3` at version `3.20.0`.
- **SpringDoc OpenAPI** (`springdoc-openapi-starter-webmvc-ui` 3.0.3) depends on `swagger-core-jakarta`, which in turn declares a transitive dependency on `commons-lang3` at version `3.19.0`.

Maven's default dependency resolution would silently select one version over the other (nearest-wins), leaving the effective classpath version unpredictable across Maven versions and build environments. The Maven Enforcer plugin's `requireUpperBoundDeps` rule (see also: [ADR-0034](0034-maven-enforcer.md) — Maven Enforcer plugin enforces dependency convergence) detects this conflict and fails the build with a convergence error, surfacing the disagreement explicitly rather than allowing a silent downgrade.

The conflict was first observed during the JJWT 0.13.0 and tooling upgrade cycle, when the addition of SpringDoc 3.0.3 introduced the `swagger-core-jakarta` transitive chain. Without an explicit pin, the Enforcer rule blocks the build.

`commons-lang3` is a transitive dependency, not a direct one. Under Requirement 1.3, it qualifies as a Significant Technology Choice because it is explicitly version-pinned in `backend/pom.xml`'s `<dependencyManagement>` section.

## Decision
Pin `org.apache.commons:commons-lang3` to version `3.20.0` in the `<dependencyManagement>` section of `backend/pom.xml`. This is not a direct dependency declaration — no `<dependency>` entry is added to the `<dependencies>` block. The pin instructs Maven to resolve all transitive requests for `commons-lang3` to `3.20.0`, satisfying the Enforcer's `requireUpperBoundDeps` rule by ensuring the selected version is at least as high as every requested version.

Version `3.20.0` is chosen because it is the higher of the two conflicting versions (`3.20.0` vs `3.19.0`), making it the correct upper-bound resolution. Pinning to the lower version (`3.19.0`) would violate `requireUpperBoundDeps` because Spring Boot BOM requests `3.20.0`.

Source manifest: `backend/pom.xml`, `<dependencyManagement>` section.

## Consequences
- **Gained (reproducibility):** The classpath version of `commons-lang3` is now deterministic across all developer machines and CI runs. Maven will always resolve `3.20.0` regardless of which transitive path is evaluated first.
- **Gained (build stability):** The Maven Enforcer `requireUpperBoundDeps` rule passes cleanly. Without this pin, every `mvn validate` (the phase at which Enforcer runs) would fail, blocking all builds.
- **Gained (correctness):** Pinning to the upper bound (`3.20.0`) ensures that any code in the Spring Boot BOM's dependency tree that relies on APIs introduced in `3.20.0` continues to work correctly, rather than being silently downgraded to `3.19.0`.
- **Cost (maintenance):** This pin must be revisited whenever either Spring Boot or SpringDoc is upgraded. If a future Spring Boot BOM advances `commons-lang3` to `3.21.0` or higher, the pin will become a downgrade constraint and will itself trigger a `requireUpperBoundDeps` failure, prompting an update. This is the intended behaviour — the Enforcer will catch the drift.
- **Mitigation:** The Maven Enforcer rule (ADR-0034) runs on every build at the `validate` phase, so any future version drift introduced by a dependency upgrade will be caught immediately rather than silently accumulating.

### Alternatives considered
- **No pin (rely on Maven nearest-wins)** — Maven would silently select whichever version appears first in the dependency graph traversal order. This is non-deterministic across Maven versions and produces a build that passes locally but may fail or behave differently in CI. Rejected because it defeats the purpose of the Enforcer rule and produces an unreliable build.
- **Pin to `3.19.0` (the lower version)** — This would satisfy SpringDoc's transitive requirement but would violate `requireUpperBoundDeps` because Spring Boot BOM requests `3.20.0`. Rejected because it introduces a deliberate downgrade that the Enforcer is specifically designed to prevent.
- **Exclude `commons-lang3` from SpringDoc's transitive tree** — Adding a `<exclusion>` to the SpringDoc dependency declaration would remove the conflicting transitive entry, allowing Spring Boot BOM's version to win uncontested. This approach is fragile: if SpringDoc's internal code relies on `commons-lang3` APIs, the exclusion could cause runtime failures. Rejected in favour of the explicit pin, which is safer and more transparent.

### References
- https://commons.apache.org/proper/commons-lang/
- https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#dependency-management
- https://maven.apache.org/enforcer/enforcer-rules/requireUpperBoundDeps.html
