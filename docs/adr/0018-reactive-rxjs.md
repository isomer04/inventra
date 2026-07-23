+++
adr = "0018"

[[covers]]
id = "rxjs"
version = "~7.8.0"
manifest = "frontend/package.json"
+++

# ADR-0018: Reactive Programming — RxJS 7.8

## Status
Accepted

## Context
Inventra is a multi-tenant inventory and order management SaaS that requires handling asynchronous data streams throughout the frontend application. The application must manage multiple concurrent concerns:

- **Real-time updates:** Inventory levels, order statuses, and user notifications need to be pushed from the backend and reflected in the UI without manual polling or page refreshes.
- **Complex async workflows:** User actions like submitting an order involve multiple sequential and parallel API calls (validate inventory, reserve stock, process payment, update order status) that must be coordinated and error-handled consistently.
- **Event composition:** UI interactions (search input, filter changes, pagination) need to be debounced, throttled, and combined with backend responses to provide a responsive user experience.
- **Memory management:** Long-lived subscriptions (WebSocket connections, polling intervals) must be properly cleaned up when components are destroyed to prevent memory leaks.
- **Type safety:** Asynchronous operations must maintain type safety throughout the data pipeline, from HTTP responses to component state.

Angular's change detection and component lifecycle are built around observables, making a reactive programming library essential for the framework. RxJS (Reactive Extensions for JavaScript) is the de facto standard for reactive programming in Angular applications, providing a comprehensive set of operators for transforming, combining, and managing asynchronous data streams.

RxJS 7.8 is the latest stable release in the 7.x series, offering improved tree-shaking, better TypeScript support, and a smaller bundle size compared to RxJS 6.x. The library is a peer dependency of Angular and is tightly integrated with Angular's HTTP client, router, and forms modules.

Source manifest: `frontend/package.json`

## Decision
Use **RxJS 7.8** as the reactive programming library for Inventra. The library is declared in `frontend/package.json` as a runtime dependency:

- `rxjs` `~7.8.0`

RxJS provides the `Observable` type and a rich set of operators (`map`, `filter`, `switchMap`, `combineLatest`, `debounceTime`, etc.) for composing asynchronous data flows. The library is used throughout the Angular application for HTTP requests, router events, form value changes, and custom event streams.

The `~7.8.0` version constraint allows patch updates (7.8.x) but prevents minor version updates (7.9.x), ensuring stability while receiving bug fixes and security patches.

Source manifest: `frontend/package.json`

## Consequences
- **Gained (declarative async composition):** RxJS operators enable declarative composition of complex asynchronous workflows. For example, a search feature can be implemented as `searchInput.valueChanges.pipe(debounceTime(300), distinctUntilChanged(), switchMap(query => this.api.search(query)))` — a single expression that debounces user input, cancels in-flight requests, and handles errors. This is far more maintainable than imperative callback-based code with manual state tracking.
- **Gained (automatic memory management):** RxJS subscriptions can be automatically cleaned up using Angular's `async` pipe or the `takeUntil` operator. This prevents memory leaks from long-lived subscriptions (WebSocket connections, polling intervals) that would otherwise persist after components are destroyed.
- **Gained (Angular integration):** Angular's HTTP client (`HttpClient`) returns observables by default, and the router, forms, and change detection system all expose observable APIs. Using RxJS ensures seamless integration with Angular's core features without adapter code or type conversions.
- **Gained (type safety):** RxJS observables are fully typed in TypeScript, preserving type information through the entire data pipeline. A `Observable<User>` transformed with `map(user => user.name)` becomes `Observable<string>`, catching type errors at compile time rather than runtime.
- **Gained (operator ecosystem):** RxJS provides 100+ operators for common async patterns (retry logic, rate limiting, caching, error handling). This eliminates the need to write custom async utilities and ensures consistent behavior across the application.
- **Cost (learning curve):** RxJS has a steep learning curve due to its functional programming paradigm and large operator surface area. Developers unfamiliar with reactive programming often struggle with concepts like hot vs. cold observables, subscription management, and operator chaining. New team members typically need 2-3 weeks to become comfortable with RxJS patterns.
- **Cost (debugging complexity):** Debugging RxJS pipelines is more difficult than debugging imperative code. Stack traces often point to internal RxJS operators rather than application code, and understanding the execution order of nested observables requires mental modeling of the subscription graph. The RxJS DevTools browser extension helps but adds overhead.
- **Cost (bundle size):** RxJS 7.8 adds approximately 30-40 KB (gzipped) to the bundle, even with tree-shaking. This is acceptable for Inventra's business SaaS use case but would be significant for a content site where initial load time is critical.
- **Cost (over-engineering risk):** RxJS's power can lead to over-engineering simple async operations. A single HTTP request does not need `retry`, `timeout`, and `catchError` operators unless the use case requires it. Code reviews must enforce simplicity and reject unnecessary operator chains.
- **Mitigation (learning curve):** The team uses Angular's official RxJS guide and the "RxJS Operators Decision Tree" as reference materials. Code reviews enforce consistent patterns (prefer `async` pipe over manual subscriptions, use `takeUntil` for cleanup, avoid nested subscriptions). Pair programming sessions help onboard new developers.
- **Mitigation (debugging complexity):** The team uses the RxJS DevTools browser extension in development to visualize observable streams. Complex pipelines are broken into smaller, named functions with explicit types to improve stack traces and readability.
- **Mitigation (over-engineering):** Code reviews enforce the "simplest solution" principle — if a single `subscribe()` call is sufficient, operators like `switchMap` or `combineLatest` are not added preemptively. RxJS is used for genuinely complex async workflows (multi-step forms, real-time updates) but not for simple one-off HTTP requests.

### Alternatives considered
- **Promises (native)** — JavaScript's built-in `Promise` type provides basic async handling and is simpler to learn than RxJS. Rejected because promises are single-value and cannot be canceled or retried without additional libraries. Angular's HTTP client returns observables, so using promises would require converting every HTTP call with `toPromise()` or `firstValueFrom()`, adding boilerplate and losing RxJS's operator ecosystem.
- **async/await (native)** — Modern JavaScript syntax for writing asynchronous code in a synchronous style. Rejected for the same reasons as promises — `async/await` works with promises, which lack the composability and cancellation features needed for Inventra's real-time updates and complex workflows. `async/await` is still used within RxJS operators (e.g., `switchMap(async () => ...)`) where appropriate.
- **Bacon.js** — An alternative reactive programming library with a simpler API than RxJS. Rejected because Bacon.js is not actively maintained (last release in 2020) and has poor TypeScript support. RxJS is the de facto standard in the Angular ecosystem, ensuring better community support and integration.
- **Most.js** — A high-performance reactive library with a smaller bundle size than RxJS. Rejected because Most.js is not compatible with Angular's observable-based APIs without adapter code. The performance benefits (10-20% faster in benchmarks) are negligible for Inventra's use case, where network latency dominates execution time.
- **xstream** — A minimalist reactive library designed for Cycle.js. Rejected for the same reasons as Most.js — incompatible with Angular's ecosystem and insufficient community support. RxJS's larger bundle size is justified by its comprehensive operator set and Angular integration.

### References
- https://rxjs.dev/
- https://angular.dev/guide/observables
- https://rxjs.dev/operator-decision-tree
- https://github.com/ReactiveX/rxjs

