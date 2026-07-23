+++
adr = "0032"

[[covers]]
id = "org.jacoco:jacoco-maven-plugin"
version = "${jacoco-maven-plugin.version}"
manifest = "backend/pom.xml"
+++

# ADR-0032: JaCoCo Code Coverage

## Status
Accepted

## Context
Inventra's backend requires automated code coverage measurement to assess test suite effectiveness and identify untested code paths. Code coverage metrics help the team:

- **Identify gaps in test coverage** by highlighting which classes, methods, and branches lack test execution
- **Track coverage trends** over time to ensure new features include adequate tests
- **Enforce coverage standards** for critical modules (authentication, tenant isolation, order processing)
- **Guide testing efforts** by revealing which code paths are exercised by the test suite

Without automated coverage measurement, the team cannot objectively assess whether tests adequately exercise the codebase. Manual inspection of test files cannot reveal which branches, exception handlers, or edge cases remain untested.

The coverage tool must:
- **Integrate with Maven** to run automatically during the build lifecycle
- **Support Java 25** and modern JVM bytecode features
- **Generate HTML reports** for human review and CI/CD artifacts
- **Instrument code at runtime** without requiring source code modifications
- **Measure multiple coverage dimensions** including line coverage, branch coverage, and method coverage

JaCoCo (Java Code Coverage) version `0.8.14` is the latest stable release, providing full support for Java 25 bytecode and modern language features. JaCoCo instruments bytecode at runtime using a Java agent, collecting coverage data during test execution without modifying source files.

The plugin binds to Maven's `initialize` phase (via `prepare-agent` goal) to set up instrumentation before tests run, and to the `test` phase (via `report` goal) to generate HTML reports at `target/site/jacoco/index.html` after test execution completes.

Source manifest: `backend/pom.xml`, `<properties>` section declares `jacoco-maven-plugin.version` as `0.8.14`, and `<build><plugins>` section configures the plugin with `prepare-agent` and `report` executions.

## Decision
Use **JaCoCo Maven Plugin** version `0.8.14` for backend code coverage analysis.

Source manifest: `backend/pom.xml`, `<properties>` and `<build><plugins>` sections.

## Consequences
- **Gained (visibility into test coverage):** JaCoCo generates detailed HTML reports showing line-by-line coverage, branch coverage percentages, and method coverage for every class. Developers can quickly identify untested code paths and prioritize testing efforts.
- **Gained (automated coverage collection):** JaCoCo runs automatically during `mvn test`, requiring no manual intervention. Coverage data is collected every time tests run, ensuring metrics stay current as the codebase evolves.
- **Gained (multiple coverage dimensions):** JaCoCo measures line coverage (which lines executed), branch coverage (which if/else paths taken), method coverage (which methods invoked), and complexity coverage (cyclomatic complexity of tested paths). This multi-dimensional view reveals different types of coverage gaps.
- **Gained (Java 25 support):** JaCoCo 0.8.14 fully supports Java 25 bytecode, including modern language features like pattern matching, sealed classes, and record patterns. This ensures accurate coverage measurement for Inventra's Java 25 codebase.
- **Gained (CI/CD integration):** JaCoCo reports can be published as CI/CD artifacts, allowing the team to track coverage trends over time and enforce coverage gates (e.g., fail builds if coverage drops below a threshold). The HTML reports are human-readable and can be archived for historical analysis.
- **Gained (zero source code changes):** JaCoCo instruments bytecode at runtime using a Java agent, requiring no annotations, interfaces, or other modifications to production code. Tests run normally, and coverage data is collected transparently.
- **Cost (build time overhead):** JaCoCo instrumentation and report generation add time to the test phase. For large codebases, coverage collection can increase test execution time by 10-20%, slowing down local development and CI/CD pipelines.
- **Cost (coverage metrics can mislead):** High coverage percentages do not guarantee test quality. Tests may execute code without asserting correct behavior, or may test trivial getters/setters while missing complex business logic. Teams may focus on coverage numbers rather than meaningful test scenarios.
- **Cost (report storage):** JaCoCo generates HTML reports with detailed line-by-line coverage data, which can consume significant disk space in CI/CD artifact storage. For projects with frequent builds, report storage costs can accumulate over time.
- **Mitigation (build time):** JaCoCo's runtime instrumentation is efficient, and the overhead is acceptable for most projects. Teams can skip coverage collection during rapid local development (e.g., `mvn test -DskipTests=false -Djacoco.skip=true`) and run full coverage only in CI/CD or before committing code.
- **Mitigation (coverage quality):** Inventra supplements JaCoCo line/branch coverage with mutation testing (PITest) for critical modules, ensuring tests not only execute code but also detect defects. The team reviews coverage reports to identify meaningful gaps, not just chase percentage targets.
- **Mitigation (report storage):** CI/CD pipelines can retain only the latest coverage report or aggregate reports weekly, discarding intermediate artifacts. JaCoCo also supports XML output for integration with coverage tracking services (e.g., Codecov, SonarQube) that provide trend analysis without storing full HTML reports.

### Alternatives considered
- **Cobertura** — Cobertura is an older Java code coverage tool that was widely used before JaCoCo. However, Cobertura development has stalled, with the last release in 2015. It does not support Java 8+ bytecode features (lambdas, streams, modules) and fails to instrument modern Java code correctly. Rejected because it is unmaintained and incompatible with Java 25.
- **Clover** — Clover is a commercial code coverage tool from Atlassian, offering advanced features like test optimization (running only tests affected by code changes) and historical trend analysis. However, Clover requires a paid license, adds proprietary tooling to the build process, and provides diminishing returns over JaCoCo for Inventra's use case. Rejected due to licensing costs and unnecessary complexity.
- **IntelliJ IDEA Coverage** — IntelliJ IDEA includes built-in code coverage measurement that works well for local development and debugging. However, IDE-based coverage is not reproducible in CI/CD pipelines, cannot be automated via Maven, and does not generate portable reports for team-wide analysis. Rejected because it does not integrate with the build system.
- **Emma** — Emma is an early Java code coverage tool that inspired JaCoCo's design. However, Emma development ceased in 2005, and it does not support Java 5+ features (generics, annotations, enums). JaCoCo was created as Emma's successor, incorporating modern Java support and active maintenance. Rejected because it is obsolete and unmaintained.

### References
- https://www.eclemma.org/jacoco/
- https://www.eclemma.org/jacoco/trunk/doc/maven.html
- https://www.eclemma.org/jacoco/trunk/doc/index.html

