+++
adr = "0024"

[[covers]]
id = "testing-library"
version = "^19.3.0"
manifest = "frontend/package.json"
+++

# ADR-0024: Frontend Component Testing — Testing Library

## Status
Accepted

## Context
Inventra's frontend is an Angular 21 application with complex UI components for inventory management, order processing, tenant switching, and data visualisation. These components must be tested to ensure they render correctly, respond to user interactions, and integrate properly with services and state management. Component testing requires:

- **User-centric testing:** Tests should interact with components the way users do—clicking buttons, filling forms, reading displayed text—rather than testing implementation details like internal state or method calls. This makes tests more resilient to refactoring and better aligned with actual user behaviour.
- **Accessibility focus:** Tests should encourage accessible component design by querying elements using accessible roles, labels, and text content rather than CSS classes or test IDs. This ensures components work correctly with assistive technologies.
- **Angular integration:** The testing library must work with Angular's dependency injection, change detection, and template compilation. It must support testing components that use Angular directives (`*ngIf`, `*ngFor`, `[ngClass]`), reactive forms, and RxJS observables.
- **Framework compatibility:** The testing library must integrate with Vitest (see [ADR-0023](0023-frontend-unit-testing-vitest.md)) and jsdom (see [ADR-0025](0025-frontend-test-environment-jsdom.md)), running tests in a Node.js environment without requiring a real browser.

Angular's built-in testing utilities (`TestBed`, `ComponentFixture`) provide low-level access to component instances and change detection, but they encourage testing implementation details. For example, a typical `TestBed` test might call `component.onSubmit()` directly and assert on `component.isLoading`, rather than clicking the submit button and checking if a loading spinner appears in the DOM. This approach makes tests brittle—refactoring the component's internal state breaks tests even if the user-facing behaviour is unchanged.

Testing Library is a family of testing utilities that encourage user-centric testing across frameworks (React, Vue, Angular, Svelte). The core philosophy is "the more your tests resemble the way your software is used, the more confidence they can give you." Testing Library provides:

- **DOM queries by accessibility attributes:** `getByRole('button')`, `getByLabelText('Email')`, `getByText('Submit')` encourage querying elements the way screen readers and users do, rather than by CSS classes or test IDs.
- **User event simulation:** `userEvent.click()`, `userEvent.type()` simulate real user interactions with proper event sequencing (focus, input, blur) rather than dispatching synthetic events.
- **Async utilities:** `waitFor()`, `findByRole()` handle asynchronous updates (HTTP requests, animations, debounced inputs) without manual `setTimeout` or `tick()` calls.

`@testing-library/angular` version `^19.3.0` is the Angular-specific adapter for Testing Library, providing a `render()` function that wraps Angular's `TestBed` and returns Testing Library queries. Version 19.3.0 is compatible with Angular 21 and Vitest 4.x.

`@testing-library/dom` version `^10.4.1` is the core DOM testing library that provides the query functions (`getByRole`, `getByText`, etc.) and async utilities (`waitFor`, `waitForElementToBeRemoved`). `@testing-library/angular` depends on this package and re-exports its utilities.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Decision
Use **@testing-library/angular** version `^19.3.0` and **@testing-library/dom** version `^10.4.1` for frontend component testing.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Consequences

**Advantages:**

- **User-centric tests:** Testing Library encourages writing tests that interact with components through the DOM, the same way users do. For example, testing Inventra's `LoginFormComponent` involves typing into email and password inputs, clicking the submit button, and asserting that a success message appears—not calling `component.login()` and checking `component.isAuthenticated`. This makes tests more meaningful and aligned with actual user behaviour.
- **Refactoring resilience:** Because tests interact with the public interface (the rendered DOM) rather than internal implementation (component properties, private methods), they remain valid when refactoring component internals. For example, renaming `isLoading` to `loading` or changing from a boolean flag to a state enum does not break tests, as long as the loading spinner still appears in the DOM.
- **Accessibility enforcement:** Testing Library's query functions (`getByRole`, `getByLabelText`) encourage accessible component design. If a test cannot find a button using `getByRole('button')`, it indicates the button is not properly marked up for screen readers. For Inventra, this helps ensure that inventory forms, order tables, and navigation menus are accessible to users with disabilities.
- **Async handling:** Testing Library's `waitFor()` and `findBy*` queries automatically retry until elements appear or conditions are met, with a default timeout of 1000ms. This eliminates the need for manual `tick()` calls or `setTimeout` in tests that involve HTTP requests, debounced inputs, or animations. For example, testing Inventra's `ProductSearchComponent` (which debounces search input by 300ms) is as simple as `await userEvent.type(searchInput, 'widget'); await screen.findByText('Widget A');`—no manual timing logic required.
- **Framework-agnostic patterns:** Testing Library's API is consistent across React, Vue, Angular, and Svelte, so developers familiar with Testing Library in one framework can apply the same patterns in another. This reduces onboarding time for developers joining Inventra from projects using different frameworks.
- **Integration with Vitest:** `@testing-library/angular` works seamlessly with Vitest's test runner and jsdom environment. The `render()` function sets up Angular's `TestBed`, compiles the component, and returns Testing Library queries, all within Vitest's fast Node.js-based test execution.

**Disadvantages / costs / risks:**

- **Learning curve for Angular developers:** Developers accustomed to Angular's `TestBed` API must learn Testing Library's query functions and async utilities. The shift from testing component properties (`expect(component.isLoading).toBe(true)`) to testing DOM state (`expect(screen.getByRole('status')).toHaveTextContent('Loading...')`) requires a mindset change. However, the Inventra team considers this a worthwhile investment because user-centric tests provide more confidence and are easier to maintain long-term.
- **Limited access to component internals:** Testing Library intentionally does not expose the component instance, making it difficult to test implementation details like private methods or internal state. For rare cases where testing internals is necessary (e.g., testing a complex state machine in a service), the team uses Angular's `TestBed` directly rather than Testing Library. This is acceptable because most component tests should focus on user-facing behaviour, not internals.
- **Query debugging:** When a query fails (e.g., `getByRole('button', { name: 'Submit' })` does not find the button), the error message shows the entire DOM tree, which can be overwhelming for large components. Testing Library provides a `screen.debug()` utility to print the current DOM, but developers must learn to read and interpret the output. The team mitigates this by writing small, focused tests that render only the component under test, keeping the DOM tree manageable.
- **Async timing issues:** While `waitFor()` handles most async scenarios, it can produce flaky tests if the timeout is too short or if the condition never becomes true. For example, if a test waits for an element that never appears due to a bug, the test will hang for 1000ms before failing. The team mitigates this by setting appropriate timeouts in `vitest.config.ts` and using `findBy*` queries (which have built-in waiting) instead of `getBy*` queries for elements that appear asynchronously.

**Mitigations applied:**

- The team uses Testing Library's `screen` object (e.g., `screen.getByRole('button')`) rather than destructuring queries from `render()`, making tests more consistent and easier to refactor.
- For components that require complex setup (e.g., mocking HTTP services, providing route parameters), the team creates reusable test utilities (e.g., `renderWithProviders()`) that wrap `render()` and set up common dependencies.
- The team uses `@testing-library/user-event` (a companion library to Testing Library) for simulating user interactions, as it provides more realistic event sequencing than Testing Library's built-in `fireEvent`. However, `@testing-library/user-event` is not yet declared in `frontend/package.json` and will be added in a future ADR if needed.

### Alternatives considered

- **Angular TestBed (alone)** — Angular's built-in testing utilities provide low-level access to component instances, change detection, and dependency injection. Rejected as the sole testing approach because `TestBed` encourages testing implementation details (component properties, method calls) rather than user-facing behaviour. Tests written with `TestBed` alone are brittle and break when refactoring component internals, even if the user-facing behaviour is unchanged. Testing Library wraps `TestBed` and provides a higher-level, user-centric API, so the team uses both together: Testing Library for component tests and `TestBed` directly for rare cases requiring access to component internals.
- **Cypress Component Testing** — Cypress is a browser-based end-to-end testing tool that recently added component testing support, allowing components to be tested in isolation within a real browser. Rejected because Cypress requires a real browser (Electron or Chrome), which adds 2-5 seconds of startup overhead per test run. For Inventra's unit and component tests (which number in the hundreds), this overhead is unacceptable. Cypress is better suited for end-to-end tests that require full browser APIs (e.g., Service Workers, IndexedDB), not unit-level component tests. Testing Library with jsdom provides sufficient DOM fidelity for component testing without the browser overhead.
- **Enzyme** — Enzyme is a React testing library that provides shallow rendering and direct access to component instances. Rejected because Enzyme is React-specific and has no Angular equivalent. Additionally, Enzyme's shallow rendering and instance access encourage testing implementation details, which is contrary to Testing Library's user-centric philosophy. Enzyme is also no longer actively maintained, with the last major release in 2019.
- **Playwright Component Testing** — Playwright is a browser automation tool that recently added component testing support, similar to Cypress. Rejected for the same reasons as Cypress: browser startup overhead makes it unsuitable for fast unit tests. Playwright is better suited for end-to-end tests that require multi-browser support (Chrome, Firefox, Safari) or advanced browser features.

### References
- <https://testing-library.com/docs/angular-testing-library/intro/>
- <https://testing-library.com/docs/queries/about>
- <https://testing-library.com/docs/dom-testing-library/api-async>
- <https://github.com/testing-library/angular-testing-library>
- <https://testing-library.com/docs/guiding-principles>
