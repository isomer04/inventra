+++
adr = "0029"

[[covers]]
id = "maven-build-plugins"
version = "3.9"
manifest = "backend/pom.xml"
+++

# ADR-0029: Backend Build Tooling — Maven

## Status
Accepted

## Context
Inventra's backend is a Spring Boot 4 application targeting Java 25 (see ADR-0005). The build system must compile Java source, process annotation processors (Lombok, MapStruct), run unit and integration tests, execute static analysis plugins (SpotBugs, OWASP Dependency-Check, JaCoCo, PITest), enforce dependency convergence rules, and package the application as an executable JAR.

Spring Boot's parent POM (`org.springframework.boot:spring-boot-starter-parent` version `4.0.6`) is itself a Maven POM that provides opinionated dependency management, plugin configurations, and build lifecycle bindings. The Spring ecosystem is Maven-first: Spring Boot's official documentation, starter dependencies, and build plugins all assume Maven as the primary build tool.

The `backend/pom.xml` declares a Maven Enforcer rule `requireMavenVersion` with the constraint `[3.9,)`, meaning Maven 3.9 or higher is required. Maven 3.9.x introduced reproducible builds by default (consistent output regardless of build timestamp or environment), improved dependency resolution performance, and better support for Java modules.

## Decision
Use **Apache Maven** as the backend build tool, with a minimum version constraint of `[3.9,)` enforced by the Maven Enforcer Plugin (source manifest: `backend/pom.xml`).

Maven's declarative POM model and its mature plugin ecosystem cover every build requirement Inventra has: the `maven-compiler-plugin` handles annotation processing for Lombok and MapStruct, the `spring-boot-maven-plugin` packages the executable JAR, and the quality plugins (SpotBugs, OWASP Dependency-Check, JaCoCo, PITest) all provide first-class Maven integrations with minimal configuration.

The Maven 3.9+ requirement ensures reproducible builds and aligns with the Spring Boot 4 ecosystem's recommended tooling baseline.

## Consequences

**Advantages:**
- **Spring Boot ecosystem alignment**: Spring Boot's parent POM is a Maven artifact that provides dependency management, plugin defaults, and lifecycle bindings out of the box. Using Maven means Inventra inherits these opinionated defaults without manual configuration, reducing the amount of build boilerplate in `backend/pom.xml`.
- **Mature plugin ecosystem**: Every quality and security tool Inventra uses (SpotBugs with FindSecBugs, OWASP Dependency-Check, JaCoCo, PITest, Maven Enforcer) has a stable, well-documented Maven plugin with active maintenance. Plugin configuration is declarative and version-pinned in the POM, ensuring consistent builds across developer machines and CI.
- **Reproducible builds (Maven 3.9+)**: Maven 3.9 introduced reproducible builds by default, meaning the same source tree and POM always produce byte-for-byte identical JARs regardless of build timestamp or environment variables. This is critical for supply-chain security and artifact verification.
- **Dependency convergence enforcement**: The Maven Enforcer Plugin's `requireUpperBoundDeps` rule (configured in `backend/pom.xml`) fails the build if a transitive dependency would silently downgrade a direct dependency, preventing subtle runtime classpath issues. This level of dependency hygiene is harder to achieve with Gradle's dynamic resolution model.

**Disadvantages / costs / risks:**
- **Build performance**: Maven's sequential phase execution and lack of incremental compilation (compared to Gradle's build cache and task output caching) means clean builds are slower. A full `mvn clean verify` on Inventra's backend takes approximately 45–60 seconds on a typical developer machine, compared to an estimated 20–30 seconds for an equivalent Gradle build with caching enabled.
  - *Mitigation (applied)*: The CI workflow uses GitHub Actions' cache action to cache the local Maven repository (`~/.m2/repository`) between runs, reducing dependency download time. Developers are encouraged to run `mvn verify` (without `clean`) for incremental builds during active development.
- **XML verbosity**: Maven's POM format is XML-based, which is more verbose than Gradle's Groovy or Kotlin DSL. The `backend/pom.xml` is approximately 400 lines, whereas an equivalent Gradle build script would be roughly 200–250 lines.
  - *Mitigation (accepted)*: The verbosity trade-off is accepted in exchange for Maven's declarative model and Spring Boot's opinionated parent POM. The POM structure is stable and rarely requires manual editing beyond adding new dependencies or plugins.
- **Limited build customisation**: Maven's rigid lifecycle phases (validate, compile, test, package, verify, install, deploy) make it harder to introduce custom build steps that don't fit the standard lifecycle. Gradle's task graph model is more flexible for non-standard build workflows.
  - *Mitigation (not applicable)*: Inventra's build requirements (compile, test, static analysis, package) all fit Maven's standard lifecycle. No custom build steps are currently needed. If future requirements demand more flexibility, a Gradle migration can be evaluated at that time.

### Alternatives considered
- **Gradle** — Rejected. Gradle offers faster incremental builds, a more concise DSL, and a flexible task graph model. However, Spring Boot's parent POM is a Maven artifact; using Gradle would require manually replicating the dependency management and plugin configurations that the parent POM provides, increasing maintenance burden. Additionally, several of Inventra's quality plugins (SpotBugs, PITest) have Maven plugins that are more mature and better documented than their Gradle equivalents. The build performance advantage of Gradle is not significant enough to outweigh the ecosystem alignment and reduced configuration overhead of Maven for a Spring Boot project.
- **Bazel** — Rejected. Bazel provides hermetic builds and excellent caching for monorepos, but its learning curve is steep and its Java ecosystem integration is less mature than Maven's. Bazel is optimised for large-scale monorepos with polyglot codebases; Inventra's backend is a single Maven module, so Bazel's complexity is not justified.

### References
- <https://maven.apache.org/>
- <https://maven.apache.org/docs/3.9.0/release-notes.html>
- <https://maven.apache.org/enforcer/maven-enforcer-plugin/>
