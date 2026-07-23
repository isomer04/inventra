+++
adr = "0023"

[[covers]]
id = "vitest-testing"
version = "^4.1.7"
manifest = "frontend/package.json"
+++

# ADR-0023: Frontend Unit Testing: Vitest

## Status
Accepted

## Context
Inventra's frontend codebase requires a fast, reliable unit testing framework to verify component behavior, business logic, and integration with Angular services. As a TypeScript and Angular application, the frontend needs a testing solution that:

- **Executes tests quickly** to support rapid feedback during development and CI/CD pipelines
- **Supports TypeScript natively** without complex configuration or transpilation overhead
- **Integrates with modern tooling** including ES modules, Vite, and contemporary JavaScript features
- **Provides code coverage reporting** to identify untested code paths and maintain quality standards
- **Works with Angular components** and supports testing library patterns for component testing

Without a robust testing framework, the frontend risks accumulating untested code, regression bugs, and reduced confidence in refactoring efforts. Manual testing alone cannot provide the speed, repeatability, or coverage needed for a production application.

The testing solution must integrate with:
- **Development workflow** via npm scripts (`npm run test`, `npm run test:coverage`)
- **CI/CD pipeline** to run tests automatically on every commit and pull request
- **Coverage reporting** to track test coverage metrics and enforce quality gates

Vitest version `^4.1.7` is a modern testing framework built on Vite, designed for speed and developer experience. It provides a Jest-compatible API, native TypeScript support, and fast test execution through Vite's transformation pipeline. The caret (`^`) range allows minor and patch updates within version 4.

`@vitest/coverage-v8` version `^4.1.7` provides code coverage reporting using V8's native coverage instrumentation, offering accurate coverage metrics with minimal performance overhead compared to traditional instrumentation approaches.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Decision
Use **Vitest** version `^4.1.7` with **@vitest/coverage-v8** version `^4.1.7` for frontend unit testing and coverage reporting.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Consequences
- **Gained (fast test execution):** Vitest leverages Vite's transformation pipeline and smart caching to execute tests significantly faster than traditional test runners. Tests run in milliseconds rather than seconds, providing near-instant feedback during development. This speed encourages developers to run tests frequently and practice test-driven development.
- **Gained (native TypeScript support):** Vitest handles TypeScript files natively without requiring separate transpilation steps or complex configuration. Type checking and transformation happen automatically, reducing setup complexity and eliminating common configuration errors.
- **Gained (Jest-compatible API):** Vitest provides a Jest-compatible API (`describe`, `it`, `expect`, `beforeEach`, etc.), allowing developers familiar with Jest to be immediately productive. Existing Jest tests can often be migrated with minimal changes, and the extensive Jest documentation and community knowledge apply to Vitest.
- **Gained (modern ES modules support):** Vitest natively supports ES modules, dynamic imports, and top-level await without configuration hacks or polyfills. This aligns with modern JavaScript standards and eliminates common module resolution issues that plague older test runners.
- **Gained (accurate coverage reporting):** `@vitest/coverage-v8` uses V8's native coverage instrumentation, providing accurate line, branch, function, and statement coverage metrics. V8 coverage is faster and more accurate than traditional instrumentation approaches like Istanbul, as it leverages the JavaScript engine's built-in profiling capabilities.
- **Gained (watch mode):** Vitest's watch mode intelligently re-runs only affected tests when files change, providing instant feedback during development. The watch mode UI is clean and informative, showing test results, coverage changes, and error details in real-time.
- **Gained (parallel test execution):** Vitest runs tests in parallel by default, utilizing multiple CPU cores to maximize throughput. This significantly reduces total test execution time in CI/CD pipelines, especially for large test suites.
- **Cost (ecosystem maturity):** Vitest is newer than Jest and has a smaller ecosystem of plugins, extensions, and community resources. Some advanced testing scenarios may require custom solutions or workarounds that would have existing solutions in the Jest ecosystem.
- **Cost (Angular integration complexity):** While Vitest works with Angular, it requires additional configuration compared to Angular's default Karma/Jasmine setup. Testing Angular components with Vitest requires integration with `@testing-library/angular` or similar libraries, adding configuration complexity.
- **Cost (learning curve for Jest users):** While Vitest's API is Jest-compatible, there are subtle differences in configuration, mocking, and module resolution. Developers experienced with Jest may encounter unexpected behavior and need to consult Vitest-specific documentation.
- **Mitigation (ecosystem maturity):** The Vitest ecosystem is growing rapidly, with active development and strong community support. For most common testing scenarios, Vitest provides built-in solutions or well-documented patterns. The team can contribute to the ecosystem by sharing custom solutions and patterns.
- **Mitigation (Angular integration):** The frontend project includes `@testing-library/angular` version `^19.3.0`, which provides a clean, idiomatic way to test Angular components with Vitest. The team can establish testing patterns and examples to guide developers through Angular-specific testing scenarios.
- **Mitigation (learning curve):** The team can provide Vitest-specific documentation, examples, and code review feedback to help developers transition from Jest or other testing frameworks. Vitest's official documentation is comprehensive and includes migration guides from Jest.

### Alternatives considered
- **Jest** — Jest is the most widely used JavaScript testing framework, with a mature ecosystem, extensive documentation, and broad community support. However, Jest's performance is significantly slower than Vitest, especially for TypeScript projects, due to its reliance on Babel for transpilation. Jest's ES module support is experimental and requires complex configuration. Rejected because Vitest provides better performance and native TypeScript/ES module support while maintaining Jest API compatibility.
- **Karma + Jasmine** — Karma with Jasmine was Angular's default testing setup for many years and is still used in many Angular projects. However, Karma is slow, requires launching real browsers for test execution, and has been deprecated by the Angular team in favor of modern alternatives. Jasmine's API is less expressive than Jest/Vitest, and Karma's configuration is complex. Rejected because it is deprecated and significantly slower than Vitest.
- **Mocha + Chai** — Mocha is a flexible test runner with a plugin-based architecture, and Chai provides assertion libraries. However, this combination requires assembling multiple tools (test runner, assertion library, mocking library, coverage tool) and configuring them to work together. Vitest provides all these capabilities in a single, integrated package with better performance. Rejected due to configuration complexity and slower performance.
- **AVA** — AVA is a minimalist test runner focused on simplicity and parallel execution. However, AVA has a smaller ecosystem than Jest/Vitest, lacks built-in mocking capabilities, and requires additional tools for coverage reporting. AVA's API differs significantly from Jest, increasing the learning curve. Rejected due to limited ecosystem and lack of built-in features.

### References
- https://vitest.dev/
- https://vitest.dev/guide/coverage.html
- https://vitest.dev/guide/migration.html
- https://github.com/vitest-dev/vitest
- https://testing-library.com/docs/angular-testing-library/intro/
