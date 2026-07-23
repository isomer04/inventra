+++
adr = "0020"

[[covers]]
id = "typescript"
version = "~5.9.3"
manifest = "frontend/package.json"
+++

# ADR-0020: Language — TypeScript 5.9

## Status
Accepted

## Context
Inventra is a multi-tenant inventory and order management SaaS that requires a statically-typed language for frontend development to ensure code reliability, maintainability, and developer productivity. The frontend codebase includes complex business logic for inventory tracking, order processing, user authentication, and real-time data synchronization. Without static typing, these concerns would be prone to runtime errors that could impact production users.

The language must provide:

- **Static type checking:** Catch type errors, null references, and property access mistakes at compile time rather than in production.
- **IDE support:** Enable intelligent code completion, refactoring tools, and inline documentation to improve developer productivity.
- **Framework compatibility:** Integrate seamlessly with Angular 21 (see [ADR-0015](0015-frontend-framework-angular.md)), which is built on TypeScript and leverages its type system for dependency injection, decorators, and template type checking.
- **Ecosystem maturity:** Support modern JavaScript features (async/await, modules, destructuring) while maintaining compatibility with the npm ecosystem.
- **Gradual adoption:** Allow incremental migration from JavaScript if needed, though Inventra is a greenfield project starting with TypeScript from day one.

TypeScript 5.9 is the latest stable release of the TypeScript language, providing static type checking, modern JavaScript features, and a powerful type system. The language is a superset of JavaScript, meaning all valid JavaScript is valid TypeScript, but TypeScript adds optional type annotations, interfaces, generics, and compile-time type checking.

TypeScript is declared in `frontend/package.json` at version `~5.9.3` (allowing patch updates but locking the minor version). The version is also pinned in the `overrides` section to ensure all transitive dependencies use the same TypeScript version, preventing version conflicts in the dependency tree.

Source manifest: `frontend/package.json`

## Decision
Use **TypeScript 5.9** as the primary language for Inventra's frontend codebase. The language is declared in `frontend/package.json` as a development dependency:

```json
"devDependencies": {
  "typescript": "~5.9.3"
},
"overrides": {
  "typescript": "~5.9.3"
}
```

TypeScript 5.9 introduces performance improvements, better type inference for conditional types, and enhanced support for ECMAScript decorators (used extensively by Angular). The `~5.9.3` version constraint allows patch updates (e.g., 5.9.4, 5.9.5) for bug fixes while preventing breaking changes from minor version updates (e.g., 5.10.0).

The `overrides` field ensures that all dependencies (including Angular packages, testing libraries, and build tools) use the same TypeScript version, preventing subtle type incompatibilities that can arise when different packages compile against different TypeScript versions.

Source manifest: `frontend/package.json`

## Consequences
- **Gained (type safety):** TypeScript's static type system catches entire classes of bugs at compile time — null reference errors, type mismatches, missing properties, and incorrect function arguments. This is critical for Inventra's business logic, where a runtime error in inventory calculations or order processing could corrupt data or lose revenue.
- **Gained (IDE support):** TypeScript enables intelligent code completion, inline documentation, and refactoring tools in modern IDEs (VS Code, WebStorm). Developers can navigate the codebase with "Go to Definition," rename symbols across files, and see type errors in real time without running the compiler.
- **Gained (framework integration):** Angular is built on TypeScript and leverages its type system for dependency injection, decorators, and template type checking. Using TypeScript ensures full compatibility with Angular's APIs and enables compile-time validation of component templates (e.g., catching typos in property bindings or incorrect event handler signatures).
- **Gained (ecosystem compatibility):** TypeScript is a superset of JavaScript, meaning all npm packages (even those written in JavaScript) can be used in TypeScript projects. The DefinitelyTyped repository provides type definitions for thousands of JavaScript libraries, enabling type-safe usage of third-party code.
- **Gained (modern JavaScript features):** TypeScript supports the latest ECMAScript features (async/await, optional chaining, nullish coalescing, top-level await) and compiles them down to older JavaScript versions for browser compatibility. This allows developers to write modern code without worrying about browser support.
- **Cost (compilation step):** TypeScript requires a compilation step to transform `.ts` files into `.js` files that browsers can execute. This adds complexity to the build process and increases build times (though Angular's incremental compilation mitigates this). For Inventra, this is acceptable because the type safety benefits far outweigh the build time cost.
- **Cost (learning curve):** Developers unfamiliar with static typing need to learn TypeScript's type system (interfaces, generics, union types, type guards). This adds 1-2 weeks to onboarding time compared to plain JavaScript. However, the team's existing experience with typed languages (Java, C#) reduces this cost.
- **Cost (type definition maintenance):** Third-party JavaScript libraries without official TypeScript support require manual type definitions or community-maintained types from DefinitelyTyped. These definitions can lag behind library updates or contain inaccuracies. For Inventra, this is mitigated by preferring libraries with official TypeScript support (e.g., RxJS, Chart.js).
- **Mitigation (compilation step):** Angular CLI handles TypeScript compilation automatically, with incremental builds and watch mode for fast feedback during development. The production build uses ahead-of-time (AOT) compilation to optimize bundle size and runtime performance.
- **Mitigation (learning curve):** The team uses TypeScript's official handbook and Angular's TypeScript guide as onboarding materials. Code reviews enforce consistent type usage (avoiding `any`, preferring interfaces over type aliases for object shapes) to maintain type safety across the codebase.

### Alternatives considered
- **JavaScript (ES2022+)** — The native language of the web, with no compilation step and a lower learning curve. Rejected because JavaScript lacks static type checking, making it unsuitable for Inventra's complex business logic. Runtime errors in inventory calculations or order processing could corrupt data or lose revenue. TypeScript's compile-time type checking prevents these errors before code reaches production.
- **Flow** — Facebook's static type checker for JavaScript, offering similar type safety to TypeScript. Rejected because Flow's ecosystem is much smaller than TypeScript's — fewer IDE integrations, fewer type definitions for third-party libraries, and less community support. Flow's development has also slowed significantly since Facebook shifted focus to TypeScript.
- **ReScript (formerly ReasonML)** — A functional language that compiles to JavaScript, offering stronger type safety than TypeScript (no `any` escape hatch, exhaustive pattern matching). Rejected because ReScript's syntax is unfamiliar to the team (OCaml-based rather than JavaScript-based), and its ecosystem is too small for enterprise use. Interoperating with JavaScript libraries requires manual bindings, increasing maintenance burden.
- **Dart** — Google's language for Flutter, offering static typing and modern language features. Rejected because Dart is primarily used for mobile development, not web development. While Dart can compile to JavaScript, its web ecosystem is minimal compared to TypeScript's. Using Dart would also prevent leveraging the npm ecosystem and Angular's TypeScript-first APIs.

### References
- https://www.typescriptlang.org/
- https://www.typescriptlang.org/docs/handbook/release-notes/typescript-5-9.html
- https://github.com/microsoft/TypeScript
- https://angular.dev/guide/typescript-configuration
