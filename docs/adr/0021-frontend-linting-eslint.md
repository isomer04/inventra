+++
adr = "0021"

[[covers]]
id = "eslint-tooling"
version = "^10.4.1"
manifest = "frontend/package.json"
+++

# ADR-0021: Frontend Linting: ESLint

## Status
Accepted

## Context
Inventra's frontend codebase requires automated code quality enforcement to maintain consistency, catch common errors, and enforce best practices across the development team. As a TypeScript and Angular application, the frontend needs a linting solution that understands:

- **TypeScript syntax** including type annotations, interfaces, generics, and decorators
- **Angular-specific patterns** such as component lifecycle hooks, dependency injection, template syntax, and framework conventions
- **Modern JavaScript** features (ES2022+) used throughout the codebase
- **HTML templates** embedded in Angular components

Without automated linting, code quality issues accumulate: inconsistent formatting, unused variables, potential bugs (like missing `await` keywords), accessibility violations in templates, and deviations from Angular style guide recommendations. Manual code review alone cannot catch all these issues reliably.

The linting solution must integrate with:
- **Development workflow** via npm scripts (`npm run lint`, `npm run lint:fix`)
- **IDE integration** providing real-time feedback as developers write code
- **CI/CD pipeline** to enforce quality gates before merging code

ESLint version `^10.4.1` is the latest major version, providing the core linting engine with support for flat configuration files and modern JavaScript parsing. The caret (`^`) range allows minor and patch updates within version 10.

`angular-eslint` version `^21.0.0` provides Angular-specific rules and integrates ESLint with Angular CLI, replacing the deprecated TSLint that Angular previously used. Version 21 aligns with Angular 21 framework compatibility.

`typescript-eslint` version `^8.60.0` enables ESLint to parse and lint TypeScript code, providing type-aware linting rules that leverage TypeScript's type checker for deeper analysis.

`@eslint/js` version `^10.0.1` provides ESLint's recommended JavaScript rule configurations, serving as the foundation for custom rule sets.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Decision
Use **ESLint** version `^10.4.1` with **angular-eslint** version `^21.0.0`, **typescript-eslint** version `^8.60.0`, and **@eslint/js** version `^10.0.1` for frontend code linting.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Consequences
- **Gained (code consistency):** ESLint enforces consistent code style across the team, including naming conventions, indentation, quote styles, and import ordering. This reduces cognitive load during code review and makes the codebase easier to navigate.
- **Gained (early error detection):** ESLint catches common programming errors at development time, such as unused variables, unreachable code, missing `await` keywords on promises, and incorrect use of Angular lifecycle hooks. This prevents bugs from reaching production.
- **Gained (TypeScript-aware linting):** `typescript-eslint` provides type-aware rules that leverage TypeScript's type checker, catching issues like incorrect type assertions, unsafe member access, and misuse of `any` types. These rules go beyond syntax checking to understand code semantics.
- **Gained (Angular best practices):** `angular-eslint` enforces Angular-specific conventions from the official style guide, such as component selector naming, proper use of `OnPush` change detection, and template accessibility rules. This ensures the codebase follows framework best practices.
- **Gained (template linting):** `angular-eslint` lints HTML templates embedded in components, catching accessibility issues (missing alt text, invalid ARIA attributes), template syntax errors, and incorrect property bindings. This improves application accessibility and reduces template-related bugs.
- **Gained (IDE integration):** ESLint integrates with VS Code, WebStorm, and other IDEs, providing real-time feedback as developers write code. Errors and warnings appear inline, allowing immediate fixes before committing code.
- **Gained (automated fixes):** Many ESLint rules support automatic fixing via `npm run lint:fix`, allowing developers to quickly resolve formatting issues and simple violations without manual editing.
- **Cost (build time overhead):** Running ESLint adds time to the development workflow, both in CI/CD pipelines and during local development. Linting the entire frontend codebase can take several seconds, slowing down pre-commit checks and CI builds.
- **Cost (configuration complexity):** ESLint requires configuration files (`eslint.config.js` or similar) to define rule sets, parser options, and plugin settings. Maintaining this configuration as ESLint, TypeScript, and Angular evolve requires ongoing effort.
- **Cost (false positives):** Some ESLint rules may flag legitimate code patterns as violations, requiring developers to add `eslint-disable` comments or adjust rule configurations. Overly strict rules can frustrate developers and slow down development.
- **Mitigation (build time):** ESLint caches results between runs, only re-linting changed files. CI/CD pipelines can run linting in parallel with other checks (tests, builds) to minimize total pipeline time. Developers can run linting on staged files only using pre-commit hooks.
- **Mitigation (configuration):** `angular-eslint` provides recommended rule sets that work out of the box for most Angular projects, reducing initial configuration effort. The team can start with recommended rules and gradually customize as needed.
- **Mitigation (false positives):** ESLint allows disabling specific rules on a per-file or per-line basis using comments. The team can adjust rule severity (error vs. warning) or disable problematic rules entirely if they cause more harm than good.

### Alternatives considered
- **TSLint** — TSLint was the original TypeScript linting tool and was widely used in Angular projects before Angular 11. However, the TSLint project was officially deprecated in 2019, with the maintainers recommending migration to ESLint. TSLint no longer receives updates, lacks support for modern TypeScript features, and has no integration with Angular 21. Rejected because it is deprecated and unsupported.
- **Biome** — Biome is a modern linting and formatting tool written in Rust, offering faster performance than ESLint. However, Biome has limited Angular-specific rules, lacks the ecosystem of ESLint plugins, and has less mature IDE integration. The Angular community has standardized on ESLint via `angular-eslint`, making Biome a non-standard choice. Rejected due to limited Angular support and smaller ecosystem.
- **Standard JS** — Standard JS is an opinionated JavaScript linter with zero configuration, enforcing a specific code style. However, Standard JS does not support TypeScript out of the box, lacks Angular-specific rules, and its opinionated style may conflict with Angular style guide recommendations. Rejected because it does not support TypeScript or Angular patterns.
- **Prettier (alone)** — Prettier is a code formatter that enforces consistent formatting but does not perform linting (error detection). While Prettier complements ESLint, it cannot replace ESLint's error detection, type-aware rules, or Angular-specific checks. Rejected as a standalone solution because it does not catch programming errors or enforce framework best practices.

### References
- https://eslint.org/
- https://github.com/angular-eslint/angular-eslint
- https://typescript-eslint.io/
- https://eslint.org/docs/latest/use/configure/
- https://angular.dev/style-guide
