+++
adr = "0028"

[[covers]]
id = "axe-core"
version = "^4.10.2"
manifest = "frontend/package.json"
+++

# ADR-0028: Frontend Accessibility Testing: axe-core

## Status
Accepted

## Context
Inventra's frontend application must be accessible to users with disabilities, complying with WCAG 2.1 Level AA standards. Accessibility issues such as missing ARIA labels, insufficient color contrast, keyboard navigation problems, and improper heading hierarchies can prevent users with disabilities from effectively using the application. Manual accessibility testing alone is insufficient because:

- **Scale:** Manual testing cannot cover all components, pages, and user flows consistently across every code change
- **Expertise:** Identifying accessibility violations requires specialized knowledge of WCAG guidelines, ARIA specifications, and assistive technology behavior
- **Regression:** Accessibility issues can be reintroduced during refactoring or feature development without automated checks
- **Feedback speed:** Developers need immediate feedback during development to fix issues before they reach production

The accessibility testing solution must:
- **Integrate with the test suite** to run automatically during development and in CI/CD pipelines
- **Detect common violations** such as missing alt text, invalid ARIA attributes, color contrast issues, and keyboard accessibility problems
- **Provide actionable feedback** with clear descriptions of violations and remediation guidance
- **Support Angular components** by testing rendered DOM output in the test environment

axe-core version `^4.10.2` is a widely-adopted accessibility testing engine developed by Deque Systems, the industry leader in accessibility tools. It implements automated checks for WCAG 2.1 Level A and AA success criteria, Section 508 standards, and best practices. The library runs in the browser or test environment, analyzing the DOM to detect accessibility violations.

Version 4.10.2 includes:
- **90+ accessibility rules** covering WCAG 2.1 Level A and AA requirements
- **Zero false positives** design philosophy, reporting only confirmed violations
- **Detailed violation reports** with impact levels, WCAG references, and remediation guidance
- **Framework-agnostic** operation, working with any JavaScript testing framework

The caret (`^`) range allows minor and patch updates within version 4, ensuring the project receives new rules and bug fixes while maintaining API compatibility.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Decision
Use **axe-core** version `^4.10.2` for automated accessibility testing in the frontend test suite.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Consequences
- **Gained (automated WCAG compliance):** axe-core automatically detects violations of WCAG 2.1 Level A and AA success criteria, including missing alt text, invalid ARIA attributes, insufficient color contrast, and improper heading hierarchies. This ensures Inventra meets accessibility standards without requiring manual audits for every change.
- **Gained (early detection):** Accessibility violations are caught during unit and integration tests, providing immediate feedback to developers. This prevents accessibility issues from reaching production and reduces the cost of remediation compared to fixing issues discovered in manual audits or user reports.
- **Gained (developer education):** axe-core violation reports include detailed descriptions, WCAG references, and remediation guidance. Developers learn accessibility best practices through actionable feedback, improving their ability to write accessible code from the start.
- **Gained (regression prevention):** Automated accessibility tests run in CI/CD pipelines, preventing previously fixed accessibility issues from being reintroduced during refactoring or feature development. This maintains accessibility quality over time.
- **Gained (component-level testing):** axe-core integrates with Vitest and Testing Library, allowing accessibility testing of individual Angular components in isolation. This enables developers to verify accessibility during component development rather than waiting for full application testing.
- **Gained (industry-standard rules):** axe-core is developed by Deque Systems, the leading accessibility tool vendor, and is used by major organizations including Microsoft, Google, and government agencies. The rule set is continuously updated to reflect current WCAG interpretations and best practices.
- **Cost (incomplete coverage):** axe-core detects approximately 57% of WCAG issues automatically (per Deque's research). Manual testing with assistive technologies (screen readers, keyboard navigation) is still required to catch issues like focus management, screen reader announcements, and complex interaction patterns. Developers may mistakenly believe passing axe-core tests means full accessibility compliance.
- **Cost (test execution time):** Running axe-core analysis adds time to the test suite, typically 100-500ms per component test. For large test suites with hundreds of components, this can add several seconds to total test execution time.
- **Cost (false negatives):** axe-core prioritizes zero false positives, meaning it only reports confirmed violations. This conservative approach may miss edge cases or context-dependent issues that require human judgment. Developers must not rely solely on axe-core for accessibility validation.
- **Mitigation (incomplete coverage):** Document that axe-core is one layer of accessibility testing, not a complete solution. Supplement automated tests with periodic manual audits using screen readers (NVDA, JAWS, VoiceOver) and keyboard-only navigation. Include accessibility acceptance criteria in user stories to ensure manual testing coverage.
- **Mitigation (test execution time):** Run axe-core selectively on components with user-facing UI rather than every test. Use axe-core's `runOnly` option to test specific rule sets (e.g., only WCAG Level A rules) for faster feedback during development, running full rule sets in CI/CD pipelines.
- **Mitigation (false negatives):** Combine axe-core with ESLint accessibility plugins (e.g., `eslint-plugin-jsx-a11y` for template linting) to catch issues at development time. Train developers on common accessibility patterns that automated tools cannot verify, such as focus management and screen reader testing.

### Alternatives considered
- **pa11y** — pa11y is an accessibility testing tool that wraps axe-core, HTML CodeSniffer, and other engines. However, pa11y is designed for full-page testing via headless browsers rather than component-level testing in unit tests. It adds complexity (requires launching a browser) and is slower than using axe-core directly in the test environment. Rejected because it does not integrate well with Vitest component tests.
- **Lighthouse CI** — Lighthouse includes accessibility audits powered by axe-core and can run in CI/CD pipelines. However, Lighthouse is designed for full application testing rather than component-level testing, requires a running application server, and provides less granular feedback than axe-core directly. Rejected because it does not support component-level testing during development.
- **WAVE (WebAIM)** — WAVE is a browser extension and API for accessibility testing, providing visual feedback on accessibility issues. However, WAVE is primarily a manual testing tool rather than an automated testing library, does not integrate with JavaScript test frameworks, and requires a running application. Rejected because it cannot be automated in the test suite.
- **Tenon.io** — Tenon.io is a cloud-based accessibility testing service with a comprehensive rule set. However, it requires sending HTML to a third-party API, raising privacy concerns for Inventra's application data. It also introduces network latency and requires an API key, complicating local development. Rejected due to privacy concerns and lack of offline testing support.
- **Manual testing only** — Relying solely on manual accessibility testing with screen readers and keyboard navigation provides the most comprehensive coverage. However, manual testing is time-consuming, requires specialized expertise, and cannot be run automatically on every code change. Rejected as a standalone approach because it does not scale and allows regressions.

### References
- https://github.com/dequelabs/axe-core
- https://www.deque.com/axe/core-documentation/
- https://www.w3.org/WAI/WCAG21/quickref/
- https://github.com/dequelabs/axe-core/blob/develop/doc/API.md
- https://www.deque.com/blog/automated-testing-study-identifies-57-percent-of-digital-accessibility-issues/

