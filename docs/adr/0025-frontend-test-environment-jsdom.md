+++
adr = "0025"

[[covers]]
id = "jsdom"
version = "^29.1.1"
manifest = "frontend/package.json"
+++

# ADR-0025: Frontend Test Environment — jsdom

## Status
Accepted

## Context
Inventra's frontend test suite requires a DOM (Document Object Model) implementation to execute tests for Angular components, directives, and services that interact with browser APIs. Angular components render HTML templates, manipulate the DOM through `ElementRef` and `Renderer2`, listen to DOM events (clicks, input changes, form submissions), and use browser APIs like `localStorage`, `window.location`, and `document.querySelector`.

Without a DOM environment, these tests cannot run in Node.js (the runtime used by Vitest, see [ADR-0023](0023-frontend-unit-testing-vitest.md)). Node.js provides no `window`, `document`, or DOM APIs by default. The test environment must provide:

- **DOM API implementation:** A complete implementation of the DOM specification (element creation, attribute manipulation, event dispatching, query selectors) that behaves like a real browser.
- **Browser globals:** `window`, `document`, `navigator`, `location`, `localStorage`, `sessionStorage`, and other browser-specific globals that Angular and application code expect.
- **Event system:** Support for DOM event creation, bubbling, capturing, and `addEventListener`/`removeEventListener`.
- **HTML parsing:** Ability to parse HTML strings into DOM trees, required for Angular's template compilation and Testing Library's `render()` function.
- **Performance:** Fast startup and execution, since the test suite runs on every commit and in CI. A slow DOM implementation would make the feedback loop unacceptably long.
- **Vitest integration:** Seamless integration with Vitest's test environment configuration, requiring minimal setup code.

Two categories of solution exist:

1. **Headless browser environments** (Playwright, Puppeteer, Karma with real browsers) — These launch actual browser engines (Chromium, Firefox, WebKit) in headless mode. They provide 100% browser API fidelity but are slow to start (1-3 seconds per test run), consume significant memory (100-300 MB per browser instance), and require browser binaries to be installed in CI.

2. **Pure JavaScript DOM implementations** (jsdom, happy-dom, linkedom) — These implement the DOM specification in pure JavaScript, running directly in Node.js. They start instantly (milliseconds), consume minimal memory (10-20 MB), and require no external binaries. However, they do not implement every browser API (e.g., no CSS layout engine, no actual rendering, limited support for newer APIs like Web Components).

For Inventra's use case, a pure JavaScript DOM implementation is sufficient. The frontend test suite focuses on:
- **Component logic:** Does the component correctly update its state when a button is clicked?
- **Template rendering:** Does the component render the correct HTML given specific input properties?
- **Event handling:** Does the component emit the correct events when user interactions occur?
- **Accessibility:** Does the component have correct ARIA attributes and keyboard navigation?

None of these require a real browser's layout engine or rendering pipeline. The tests do not verify visual appearance (CSS layout, pixel-perfect rendering) — that is the domain of end-to-end tests, which are not part of this unit test suite.

jsdom version `^29.1.1` is a mature, widely-adopted JavaScript implementation of the WHATWG DOM and HTML standards. It is the de facto standard DOM implementation for Node.js testing, used by Jest (the most popular JavaScript test framework), Testing Library (see [ADR-0024](0024-frontend-component-testing-testing-library.md)), and thousands of open-source projects. Version 29 adds support for modern DOM features including `AbortController`, `structuredClone`, and improved `MutationObserver` behavior.

The caret (`^`) range allows minor and patch updates within version 29, ensuring Inventra receives bug fixes and new DOM API implementations without breaking changes.

jsdom is declared in `frontend/package.json` as a development dependency. Vitest is configured to use jsdom as its test environment via the `environment: 'jsdom'` setting in `vitest.config.ts`.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Decision
Use **jsdom** version `^29.1.1` as the DOM implementation for Inventra's frontend test suite.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Consequences
- **Gained (fast test execution):** jsdom starts instantly (milliseconds) compared to headless browsers (1-3 seconds). This keeps the test feedback loop fast — developers can run the full test suite in under 10 seconds, making test-driven development practical. In CI, the frontend test job completes in under 2 minutes, allowing rapid iteration on pull requests.
- **Gained (low resource usage):** jsdom runs in the same Node.js process as Vitest, consuming only 10-20 MB of additional memory. This allows running tests in parallel without exhausting CI runner memory. Headless browsers would require 100-300 MB per instance, limiting parallelism and increasing CI costs.
- **Gained (zero external dependencies):** jsdom is a pure JavaScript library with no external binaries. CI runners require no browser installation step, and developers can run tests immediately after `npm install` without downloading Chromium or configuring browser paths. This simplifies onboarding and reduces CI setup complexity.
- **Gained (ecosystem compatibility):** jsdom is the standard DOM implementation for Node.js testing. Testing Library, MSW (see [ADR-0026](0026-frontend-api-mocking-msw.md)), and axe-core (see [ADR-0028](0028-frontend-accessibility-testing-axe-core.md)) all officially support jsdom and test against it. This ensures Inventra's testing stack works together without compatibility issues.
- **Gained (Vitest integration):** Vitest provides first-class jsdom support via the `environment: 'jsdom'` configuration option. No custom setup code is required — Vitest automatically injects `window`, `document`, and other browser globals into the test environment. This reduces boilerplate and maintenance burden.
- **Gained (WHATWG standards compliance):** jsdom implements the WHATWG DOM and HTML standards, not browser-specific quirks. This means tests verify that code works according to web standards, not according to a specific browser's implementation. Code that passes jsdom tests will work in all modern browsers (Chrome, Firefox, Safari, Edge).
- **Cost (incomplete browser API coverage):** jsdom does not implement every browser API. Notable gaps include: no CSS layout engine (no `offsetWidth`, `getBoundingClientRect` returns zeros), no actual rendering (no canvas drawing, no image loading), limited Web Components support (no Shadow DOM CSS encapsulation), and no navigation (no actual page loads). Tests that depend on these APIs will fail or require mocking.
- **Cost (behavior differences from real browsers):** jsdom's event system, timing behavior, and edge-case handling may differ subtly from real browsers. For example, jsdom's `setTimeout` is synchronous in some contexts, and its `MutationObserver` may fire callbacks in a different order than Chrome. Tests that pass in jsdom may fail in real browsers, and vice versa.
- **Cost (maintenance lag behind browser standards):** New browser APIs (e.g., View Transitions API, Popover API) take time to be implemented in jsdom. If Inventra adopts a cutting-edge browser feature, tests may fail in jsdom until the feature is implemented, requiring either mocking or waiting for a jsdom update.
- **Mitigation (incomplete API coverage):** For tests that require layout information (e.g., testing a scroll-to-top button that checks `window.scrollY`), mock the missing APIs using Vitest's `vi.spyOn()` or `vi.stubGlobal()`. For example, `vi.spyOn(element, 'getBoundingClientRect').mockReturnValue({ top: 100, ... })`. This is acceptable because unit tests should not depend on actual layout — they should verify logic, not pixels.
- **Mitigation (behavior differences):** Run a small suite of end-to-end tests (outside the scope of this ADR) using a real browser (Playwright or Cypress) to catch integration issues that jsdom cannot detect. These E2E tests run less frequently (e.g., only on `main` branch or before releases) to balance coverage with speed.
- **Mitigation (maintenance lag):** Avoid depending on cutting-edge browser APIs in application code until they are widely supported (available in all evergreen browsers for at least 6 months). This ensures jsdom has time to implement the API before Inventra adopts it. If a new API is critical, contribute to jsdom or use a polyfill in tests.

### Alternatives considered
- **happy-dom** — A newer, faster JavaScript DOM implementation that claims 2-3x better performance than jsdom. However, happy-dom has a smaller ecosystem (fewer projects use it, less community support), less mature API coverage (some DOM APIs are incomplete or buggy), and weaker Testing Library integration (some Testing Library features do not work correctly with happy-dom). Rejected because jsdom's maturity and ecosystem compatibility outweigh happy-dom's performance advantage, and Inventra's test suite is already fast enough with jsdom.
- **Playwright (headless Chromium)** — A headless browser automation tool that launches real Chromium instances for testing. Playwright provides 100% browser API fidelity, including layout, rendering, and all modern web APIs. However, Playwright is 10-50x slower than jsdom (1-3 seconds startup per test run vs. milliseconds), consumes 100-300 MB memory per browser instance (vs. 10-20 MB for jsdom), and requires downloading a 200+ MB Chromium binary in CI. Rejected because the performance cost is unacceptable for a unit test suite that runs on every commit. Playwright is better suited for end-to-end tests, which run less frequently.
- **Karma with real browsers** — Karma is a test runner that launches real browsers (Chrome, Firefox, Safari) and runs tests in them. It was the standard Angular testing solution before Angular 16. However, Karma is deprecated (the project is in maintenance mode, no new features), slow (2-5 seconds startup per test run), and requires complex configuration (browser launchers, preprocessors, plugins). Angular 16+ recommends migrating to Vitest or Jest with jsdom. Rejected because Karma is deprecated and slower than jsdom.
- **linkedom** — A lightweight JavaScript DOM implementation optimized for server-side rendering (SSR). linkedom is faster than jsdom for SSR use cases but has incomplete event system support (no event bubbling, limited `addEventListener` support) and minimal Testing Library integration. Rejected because Inventra's tests require a full event system for testing user interactions (clicks, form submissions, keyboard events), which linkedom does not provide.
- **No DOM environment (Node.js only)** — Run tests in a plain Node.js environment with no DOM APIs. This would require mocking every DOM interaction (`document.querySelector`, `element.addEventListener`, etc.) manually in every test. Rejected because the mocking burden would be enormous, tests would not verify real DOM behavior, and Testing Library (which Inventra uses, see [ADR-0024](0024-frontend-component-testing-testing-library.md)) requires a DOM environment to function.

### References
- https://github.com/jsdom/jsdom
- https://github.com/jsdom/jsdom/releases/tag/29.0.0
- https://vitest.dev/config/#environment
- https://testing-library.com/docs/dom-testing-library/intro

