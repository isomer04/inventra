+++
adr = "0033"

[[covers]]
id = "pitest"
version = "1.25.3"
manifest = "backend/pom.xml"
+++

# ADR-0033: Mutation Testing with PITest

## Status
Accepted

## Context
Inventra's backend includes critical business logic in `AuthService`, `OrderService`, `TenantService`, and `StockMovementService` that must be thoroughly tested to prevent security vulnerabilities and data corruption. Traditional code coverage metrics (line coverage, branch coverage) measure which code is executed during tests but do not measure whether tests would actually detect bugs if the code were changed. A test suite can achieve 100% line coverage while still missing critical assertions, allowing defects to pass undetected.

Mutation testing addresses this gap by systematically introducing small, deliberate bugs (mutations) into the source code and verifying that at least one test fails for each mutation. If a mutation survives (all tests still pass), it indicates a gap in test quality — either missing assertions or insufficient test cases. For Inventra's Critical-tier modules, mutation testing provides a quantitative measure of test effectiveness beyond what line coverage alone can offer.

Two Maven plugins are declared in the `mutation-test` profile in `backend/pom.xml` to enable mutation testing:

- `org.pitest:pitest-maven` (version 1.25.3) — the PITest Maven plugin, which instruments bytecode to introduce mutations, runs the test suite against each mutant, and generates an HTML report showing which mutations were killed (detected by tests) and which survived (not detected).
- `org.pitest:pitest-junit5-plugin` (version 1.2.3) — the PITest JUnit 5 integration plugin, which allows PITest to discover and execute JUnit Jupiter tests. Without this plugin, PITest would only support JUnit 4 tests.

Both plugins are profile-scoped and only execute when the `mutation-test` profile is activated via `mvn test -P mutation-test`. They contribute zero bytes to the production artifact and do not run during normal builds.

## Decision

Use PITest 1.25.3 with the JUnit 5 plugin 1.2.3 for mutation testing of Critical-tier modules (`AuthService`, `OrderService`, `TenantService`, `StockMovementService`), with a mutation score threshold of 80%.

Source manifest: `backend/pom.xml`. The plugins are declared in the `mutation-test` profile and are only active when that profile is explicitly enabled.

## Consequences

**Advantages:**

- PITest provides a quantitative measure of test quality that complements JaCoCo's line coverage metrics. For Inventra's Critical-tier modules, a mutation score of ≥80% means that at least 80% of introduced bugs are detected by the test suite, giving higher confidence that the tests would catch real regressions. This is particularly valuable for `AuthService` (where a missed assertion could allow unauthorized access) and `OrderService` (where a missed edge case could corrupt order state).
- The `DEFAULTS` mutator group applies a curated set of mutations (conditional boundary changes, negation of conditionals, return value mutations, arithmetic operator replacements) that correspond to common real-world bugs. This keeps the mutation set focused on realistic defects rather than exhaustive but low-value mutations (e.g., mutating every constant).
- PITest's incremental analysis (`withHistory=true`) caches mutation results between runs, so only changed classes and their tests are re-mutated. For Inventra this means that after the initial full run, subsequent mutation test executions complete in seconds rather than minutes, making it practical to run mutation testing on every pull request for Critical-tier modules.
- The JUnit 5 plugin allows PITest to work seamlessly with Inventra's existing JUnit Jupiter test suite (declared via `spring-boot-starter-test`). No test migration or dual test framework setup is required.

**Disadvantages / costs / risks:**

- Mutation testing is computationally expensive: PITest must run the full test suite once per mutation, and even with incremental analysis enabled, a full mutation run on all four Critical-tier modules takes 2–3 minutes on a typical developer workstation. This is acceptable for on-demand execution (`mvn test -P mutation-test`) but too slow to run on every commit. Inventra mitigates this by restricting mutation testing to Critical-tier modules only and running it as a separate CI job rather than in the main build pipeline.
- A mutation score threshold of 80% is enforced via `<mutationThreshold>80</mutationThreshold>`, which means the build will fail if test quality drops below that level. This can block pull requests if a developer adds new logic without corresponding tests. The mitigation is to treat mutation test failures as a signal to improve test coverage rather than to lower the threshold; the 80% target was chosen based on the current state of Critical-tier tests and is expected to increase over time.
- PITest operates at the bytecode level and does not understand Spring's proxy-based AOP or transaction management. Mutations introduced into `@Transactional` methods may not be detected by tests if the test relies on transaction rollback behaviour that PITest's instrumentation bypasses. Inventra mitigates this by ensuring that Critical-tier unit tests use explicit assertions on return values and state changes rather than relying solely on transactional side effects.
- The `pitest-junit5-plugin` version must be kept in sync with the JUnit 5 version shipped by Spring Boot. As of this writing, PITest 1.25.3 and `pitest-junit5-plugin` 1.2.3 are compatible with JUnit 5.11.x (the version in Spring Boot 4.0.6). Future Spring Boot upgrades may require updating the PITest plugin versions.

**Mitigations applied:**

- Mutation testing is restricted to Critical-tier modules only (`AuthService`, `OrderService`, `TenantService`, `StockMovementService`) via the `<targetClasses>` configuration, keeping execution time bounded.
- The `mutation-test` profile is not active by default, so normal builds (`mvn verify`) remain fast. Mutation testing is run on-demand or in a separate CI job.
- Incremental analysis (`withHistory=true`) is enabled to speed up repeated runs.

### Alternatives considered

- **Stryker Mutator** — a mutation testing framework with first-class support for JavaScript/TypeScript and experimental Java support. Rejected because Stryker's Java support is less mature than PITest's, and PITest is the de facto standard for JVM mutation testing with extensive Maven integration and a large community. Stryker remains the preferred choice for Inventra's Angular frontend.
- **Major** — a research-grade mutation testing tool that uses compiler-based mutation rather than bytecode instrumentation. Rejected because Major requires a custom Java compiler and is not available in Maven Central, making it impractical for a standard Maven build. PITest's bytecode approach integrates seamlessly with the existing build pipeline.
- **Manual code review** — relying on human reviewers to assess test quality rather than automated mutation testing. Rejected because manual review is subjective, inconsistent, and does not scale as the codebase grows. Mutation testing provides an objective, repeatable metric that can be enforced in CI.

### References

- <https://pitest.org/>
- <https://pitest.org/quickstart/maven/>
- <https://github.com/hcoles/pitest/releases/tag/pitest-parent-1.25.3>
- <https://github.com/pitest/pitest-junit5-plugin>
