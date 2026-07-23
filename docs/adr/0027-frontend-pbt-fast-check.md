+++
adr = "0027"

[[covers]]
id = "fast-check"
version = "^4.8.0"
manifest = "frontend/package.json"
+++

# ADR-0027: Frontend Property-Based Testing: fast-check

## Status
Accepted

## Context
Inventra's frontend codebase requires property-based testing (PBT) to complement traditional example-based unit tests. While unit tests verify specific scenarios with known inputs and expected outputs, property-based tests verify universal properties that should hold across all possible inputs. As a TypeScript and Angular application, the frontend needs a PBT solution that:

- **Generates diverse test inputs automatically** to explore edge cases and boundary conditions that developers might not anticipate
- **Shrinks failing cases** to minimal counterexamples, making it easier to diagnose and fix bugs
- **Integrates with existing test infrastructure** (Vitest) without requiring separate test runners or configuration
- **Supports TypeScript natively** with strong type inference for generated values
- **Provides rich generators** for common data types (strings, numbers, arrays, objects) and domain-specific values

Without property-based testing, the frontend risks missing edge cases that only manifest with unusual input combinations. Example-based tests can only cover scenarios that developers explicitly think to test, leaving gaps in coverage for unexpected inputs, boundary conditions, and complex state interactions.

The PBT solution must integrate with:
- **Vitest test runner** to execute property tests alongside unit tests in a unified workflow
- **Development workflow** via npm scripts (`npm run test`) without requiring separate commands
- **CI/CD pipeline** to run property tests automatically on every commit and pull request
- **TypeScript type system** to ensure generated values match expected types and catch type errors at compile time

fast-check version `^4.8.0` is a mature property-based testing library for JavaScript and TypeScript, inspired by QuickCheck (Haskell) and Hypothesis (Python). It provides a comprehensive set of generators (called "arbitraries"), automatic shrinking of failing cases, and seamless integration with popular test frameworks including Vitest. The caret (`^`) range allows minor and patch updates within version 4.

fast-check generates hundreds or thousands of random inputs for each property test, exploring the input space far more thoroughly than manual example-based tests. When a property fails, fast-check automatically shrinks the failing input to the smallest counterexample, dramatically reducing debugging time.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Decision
Use **fast-check** version `^4.8.0` for frontend property-based testing.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Consequences
- **Gained (edge case discovery):** fast-check automatically generates diverse inputs including edge cases that developers rarely think to test manually: empty strings, negative numbers, very large values, special characters, null/undefined, deeply nested structures, and unusual combinations. This discovers bugs that would otherwise only appear in production with real user data.
- **Gained (minimal counterexamples):** When a property test fails, fast-check automatically shrinks the failing input to the smallest possible counterexample. Instead of debugging a complex failing case with hundreds of elements, developers receive a minimal example that isolates the root cause. This dramatically reduces debugging time and makes bug fixes more targeted.
- **Gained (specification as code):** Property tests express universal invariants and business rules as executable specifications. For example, "serializing and deserializing data should be idempotent" or "filtering a list should never increase its length." These properties serve as living documentation that is automatically verified on every test run.
- **Gained (regression prevention):** Once a property test discovers a bug, it continues to verify that the bug stays fixed across all future code changes. The property test explores the entire input space, not just the specific failing case, providing stronger regression protection than example-based tests.
- **Gained (TypeScript integration):** fast-check provides strong TypeScript type inference for generated values. When composing arbitraries (generators), TypeScript ensures that the generated values match the expected types, catching type errors at compile time. This makes property tests type-safe and reduces runtime errors.
- **Gained (rich generator library):** fast-check includes over 100 built-in arbitraries for common types (strings, numbers, arrays, objects, dates, URLs, emails) and combinators for building complex generators. This eliminates the need to write custom random data generators for most scenarios.
- **Gained (Vitest integration):** fast-check integrates seamlessly with Vitest through its `fc.assert` API. Property tests run alongside unit tests in the same test files, use the same test runner, and appear in the same test reports. No separate configuration or test commands are required.
- **Gained (deterministic replay):** fast-check uses a seeded random number generator, allowing failed test runs to be replayed deterministically. When a property test fails in CI, developers can reproduce the exact same failure locally using the seed from the CI logs, eliminating "flaky test" issues.
- **Cost (test execution time):** Property tests run hundreds or thousands of iterations per test, making them slower than example-based unit tests. A single property test can take several seconds to complete, especially with complex generators or expensive property checks. Large property test suites can significantly increase total test execution time.
- **Cost (learning curve):** Property-based testing requires a different mindset than example-based testing. Developers must learn to think in terms of universal properties and invariants rather than specific examples. Writing effective property tests requires understanding arbitraries, shrinking, and how to express business rules as testable properties.
- **Cost (debugging complexity):** When a property test fails with a counterexample, developers must understand why that specific input violates the property. Even with shrinking, counterexamples can be non-obvious, especially for complex properties involving multiple interacting components. Debugging property test failures requires more analytical thinking than debugging example-based test failures.
- **Mitigation (test execution time):** fast-check allows configuring the number of test iterations per property (default is 100). For fast feedback during development, the team can reduce iterations to 10-20 and run full iterations (1000+) only in CI. Property tests can be tagged and run separately from unit tests if needed.
- **Mitigation (learning curve):** The team can start with simple properties (e.g., "round-tripping" properties like serialize/deserialize) and gradually introduce more complex properties as developers gain experience. Code reviews can include guidance on property test design. The team can maintain a library of reusable arbitraries and property patterns specific to Inventra's domain.
- **Mitigation (debugging complexity):** fast-check's shrinking significantly reduces debugging complexity by providing minimal counterexamples. The team can add logging to property tests to trace execution and understand why a counterexample fails. For particularly complex properties, the team can break them into smaller, more focused properties that are easier to debug.

### Alternatives considered
- **jsverify** — jsverify is an older property-based testing library for JavaScript, inspired by QuickCheck. However, jsverify is no longer actively maintained (last release in 2018), lacks TypeScript support, has a smaller generator library than fast-check, and does not integrate well with modern test runners like Vitest. Rejected due to lack of maintenance and poor TypeScript support.
- **testcheck-js** — testcheck-js is a property-based testing library inspired by Clojure's test.check. However, testcheck-js is less actively maintained than fast-check, has weaker TypeScript support, and provides fewer built-in generators. The API is less intuitive for JavaScript/TypeScript developers compared to fast-check's fluent API. Rejected due to weaker ecosystem and TypeScript support.
- **Hypothesis (via hypothesis-python)** — Hypothesis is the leading property-based testing library for Python, known for its powerful shrinking and example database. However, Hypothesis is Python-only and cannot be used directly in TypeScript/JavaScript projects. While there have been attempts to port Hypothesis to JavaScript, none have achieved the maturity or adoption of fast-check. Rejected because it is not available for JavaScript/TypeScript.
- **QuickCheck (via quickcheck-js)** — QuickCheck is the original property-based testing library from Haskell. Several JavaScript ports exist (quickcheck-js, node-quickcheck), but none are actively maintained or provide the features, TypeScript support, or ecosystem of fast-check. Rejected due to lack of maintenance and limited features.
- **Manual random testing** — The team could write custom random input generators using `Math.random()` and manually implement shrinking logic. However, this approach requires significant effort to replicate fast-check's features (diverse generators, automatic shrinking, deterministic replay, type safety). Manual random testing is error-prone and time-consuming. Rejected because fast-check provides a mature, well-tested solution out of the box.

### References
- https://fast-check.dev/
- https://github.com/dubzzz/fast-check
- https://fast-check.dev/docs/introduction/getting-started/
- https://fast-check.dev/docs/core-blocks/arbitraries/
- https://fast-check.dev/docs/advanced/shrinking/
