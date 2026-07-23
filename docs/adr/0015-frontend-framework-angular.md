+++
adr = "0015"

[[covers]]
id = "angular-framework"
version = "^21.2.15"
manifest = "frontend/package.json"

[[covers]]
id = "tslib"
version = "^2.3.0"
manifest = "frontend/package.json"
+++

# ADR-0015: Frontend Framework — Angular 21

## Status
Accepted

## Context
Inventra is a multi-tenant inventory and order management SaaS that requires a modern, enterprise-grade frontend framework capable of supporting complex business logic, real-time data updates, and a rich user interface. The frontend must handle multiple concerns:

- **Component-based architecture:** The application is composed of dozens of reusable UI components (dashboards, inventory tables, order forms, charts) that need to be maintainable and testable in isolation.
- **Type safety:** As a business-critical SaaS handling financial and inventory data, the frontend must catch errors at compile time rather than runtime.
- **Reactive data flow:** Inventory levels, order statuses, and user notifications require real-time updates and efficient change detection.
- **Enterprise ecosystem:** The framework must have mature tooling for testing, linting, build optimization, and long-term support.
- **Developer productivity:** The team needs strong IDE support, comprehensive documentation, and a large community for troubleshooting.

Angular 21 is the latest stable release of the Angular framework, providing a complete platform for building single-page applications. The framework includes core packages for component rendering (`@angular/core`, `@angular/common`, `@angular/platform-browser`), routing (`@angular/router`), forms (`@angular/forms`), animations (`@angular/animations`), and compilation (`@angular/compiler`, `@angular/compiler-cli`). The build tooling (`@angular/build`, `@angular/cli`) is also part of the framework ecosystem.

All Angular packages are versioned together and must be kept in sync. The runtime packages (`@angular/animations`, `@angular/common`, `@angular/compiler`, `@angular/core`, `@angular/forms`, `@angular/platform-browser`, `@angular/router`, `@angular/compiler-cli`) are at version `^21.2.15`, while the build tooling packages (`@angular/build`, `@angular/cli`) are at version `^21.2.13`.

Source manifest: `frontend/package.json`

## Decision
Use **Angular 21** as the frontend framework for Inventra. The framework is declared in `frontend/package.json` with the following packages:

**Runtime dependencies:**
- `@angular/animations` `^21.2.15`
- `@angular/common` `^21.2.15`
- `@angular/compiler` `^21.2.15`
- `@angular/core` `^21.2.15`
- `@angular/forms` `^21.2.15`
- `@angular/platform-browser` `^21.2.15`
- `@angular/router` `^21.2.15`

**Development dependencies:**
- `@angular/build` `^21.2.13`
- `@angular/cli` `^21.2.13`
- `@angular/compiler-cli` `^21.2.15`

Angular 21 introduces standalone components as the default (eliminating the need for NgModules in most cases), improved server-side rendering, and enhanced performance through signal-based reactivity. The framework's opinionated structure and comprehensive tooling align with Inventra's need for maintainability and long-term support.

Source manifest: `frontend/package.json`

## Consequences
- **Gained (type safety):** Angular is built on TypeScript and enforces strict typing throughout the component tree, template bindings, and service injections. This catches entire classes of bugs (null references, type mismatches, missing properties) at compile time rather than in production.
- **Gained (comprehensive ecosystem):** Angular provides a complete platform out of the box — routing, forms, HTTP client, animations, testing utilities, and build tooling are all first-party packages with consistent APIs and guaranteed compatibility. This eliminates the "JavaScript fatigue" of assembling a stack from disparate libraries.
- **Gained (enterprise support):** Angular is backed by Google and has a predictable release schedule with long-term support (LTS) versions. Major versions receive 18 months of support, providing stability for enterprise applications like Inventra.
- **Gained (reactive data flow):** Angular's change detection and RxJS integration (see [ADR-0018](0018-reactive-rxjs.md)) provide a robust foundation for handling real-time inventory updates, order status changes, and user notifications without manual DOM manipulation.
- **Gained (developer productivity):** Angular CLI automates scaffolding, testing, and build optimization. The framework's opinionated structure (components, services, modules) reduces decision fatigue and makes onboarding new developers faster.
- **Cost (bundle size):** Angular's runtime is larger than minimal frameworks like Svelte or Preact. The base bundle for a simple Angular app is approximately 150-200 KB (gzipped), compared to 10-20 KB for lighter alternatives. For Inventra, this is acceptable because the application is a complex business SaaS, not a content site where initial load time is critical.
- **Cost (learning curve):** Angular has a steeper learning curve than React or Vue due to its comprehensive feature set (dependency injection, decorators, RxJS, template syntax). New developers need 2-4 weeks to become productive, compared to 1-2 weeks for simpler frameworks. This is mitigated by Angular's excellent documentation and the team's existing TypeScript experience.
- **Cost (framework lock-in):** Angular's opinionated architecture (decorators, dependency injection, template syntax) makes it difficult to migrate components to other frameworks. Extracting business logic into framework-agnostic services mitigates this, but UI components are tightly coupled to Angular's APIs.
- **Mitigation (bundle size):** Angular's build tooling includes tree-shaking, lazy loading, and differential loading (serving optimized bundles to modern browsers). The production build is configured with `--configuration production` to enable all optimizations.
- **Mitigation (learning curve):** The team uses Angular's official tutorial and style guide as onboarding materials. Code reviews enforce consistent patterns (smart/dumb components, service-based state management) to reduce complexity.

### Alternatives considered
- **React** — The most popular frontend library, with a massive ecosystem and flexible architecture. Rejected because React is a library, not a framework — it requires assembling a stack from separate routing (React Router), state management (Redux, Zustand), and form libraries (React Hook Form, Formik). Angular's integrated approach reduces decision fatigue and ensures compatibility. React's JSX syntax is also less familiar to the team than Angular's template syntax.
- **Vue 3** — A progressive framework with a gentler learning curve than Angular and better performance than React. Rejected because Vue's ecosystem is smaller than Angular's, particularly for enterprise features like server-side rendering and advanced testing utilities. Vue's Composition API is powerful but less opinionated than Angular's dependency injection, leading to inconsistent patterns across a large codebase.
- **Svelte** — A compile-time framework with minimal runtime overhead and excellent performance. Rejected because Svelte's ecosystem is immature compared to Angular — tooling for testing, SSR, and enterprise features is still evolving. Svelte's reactive syntax is elegant but unfamiliar to the team, increasing onboarding time.
- **Solid.js** — A reactive framework with React-like syntax and better performance. Rejected for the same reasons as Svelte — the ecosystem is too young for an enterprise SaaS. Solid's fine-grained reactivity is impressive but unnecessary for Inventra's use case, where Angular's zone-based change detection is sufficient.

### References
- https://angular.dev/
- https://angular.dev/guide/releases
- https://github.com/angular/angular
- https://angular.dev/guide/standalone-components
