+++
adr = "0019"

[[covers]]
id = "angular-build-tooling"
version = "^21.2.13"
manifest = "frontend/package.json"
+++

# ADR-0019: Frontend Build Tooling — Angular CLI

## Status
Accepted

## Context
Inventra's Angular frontend requires a build system capable of handling TypeScript compilation, module bundling, code splitting, asset optimization, and development server functionality. The build tooling must support:

- **Development workflow:** Fast incremental builds with hot module replacement for rapid iteration during development.
- **Production optimization:** Tree-shaking, minification, differential loading, and lazy loading to minimize bundle size and improve load times.
- **TypeScript compilation:** Ahead-of-time (AOT) compilation of Angular templates and TypeScript code to catch errors at build time and improve runtime performance.
- **Asset management:** Processing and optimization of stylesheets, images, fonts, and other static assets.
- **Testing integration:** Support for running unit tests and generating coverage reports.
- **Consistent tooling:** A unified CLI that handles scaffolding, building, testing, and serving without requiring manual webpack or build configuration.

Angular CLI is the official command-line interface for Angular projects, providing a complete build toolchain built on top of modern bundlers. As of Angular 17+, the CLI uses `@angular/build` (powered by esbuild and Vite) as the default build system, replacing the older webpack-based builder. This new build system offers significantly faster build times while maintaining full compatibility with Angular's features.

The `@angular/cli` package provides the `ng` command-line tool, while `@angular/build` contains the actual build engine. Both packages are at version `^21.2.13` in `frontend/package.json`.

Source manifest: `frontend/package.json`

## Decision
Use **Angular CLI** (`@angular/cli` `^21.2.13`) with the **esbuild-based builder** (`@angular/build` `^21.2.13`) as the build tooling for Inventra's frontend.

The CLI is configured in `frontend/package.json` with the following scripts:
- `ng serve` — Development server with hot module replacement
- `ng build` — Production build with optimizations
- `ng build --configuration production` — Explicit production build
- `ng build --watch --configuration development` — Watch mode for development
- `ng test` — Run unit tests
- `ng test --coverage` — Generate test coverage reports

The new esbuild-based builder (`@angular/build`) provides:
- **10-20x faster builds** compared to the legacy webpack builder
- **Native ESM support** for modern browsers
- **Vite-powered dev server** with instant hot module replacement
- **Full Angular feature support** including AOT compilation, lazy loading, and differential loading

Source manifest: `frontend/package.json`

## Consequences
- **Gained (build performance):** The esbuild-based builder is 10-20x faster than the legacy webpack builder. Cold builds complete in seconds rather than minutes, and incremental rebuilds during development are nearly instantaneous. This dramatically improves developer productivity, especially when iterating on UI components.
- **Gained (zero configuration):** Angular CLI provides sensible defaults for all build settings. The team does not need to maintain webpack configurations, loader chains, or plugin setups. The CLI handles TypeScript compilation, template processing, style bundling, and asset optimization automatically.
- **Gained (integrated tooling):** The CLI provides a unified interface for all frontend tasks — scaffolding components (`ng generate`), running tests (`ng test`), serving the app (`ng serve`), and building for production (`ng build`). This eliminates the need for separate task runners like Gulp or Grunt.
- **Gained (production optimization):** The production build (`ng build --configuration production`) automatically enables tree-shaking, minification, dead code elimination, and differential loading (serving optimized bundles to modern browsers). These optimizations reduce bundle size by 40-60% compared to development builds.
- **Gained (AOT compilation):** Angular CLI uses ahead-of-time (AOT) compilation by default, compiling templates and TypeScript code at build time rather than runtime. This catches template errors early, improves runtime performance, and reduces bundle size by eliminating the Angular compiler from production bundles.
- **Cost (abstraction overhead):** The CLI abstracts away the underlying build system (esbuild, Vite), making it difficult to customize low-level build behavior. For example, adding custom webpack plugins or loaders requires ejecting from the CLI or using Angular's limited builder API. For Inventra, this is acceptable because the CLI's defaults cover all current requirements.
- **Cost (version coupling):** The CLI and build packages must be kept in sync with the Angular framework version. Mismatched versions can cause build failures or runtime errors. The team must upgrade all Angular packages together, which can be time-consuming for major version upgrades.
- **Mitigation (abstraction overhead):** Angular CLI provides an `angular.json` configuration file for common customizations (asset paths, style preprocessors, environment variables). For advanced use cases, the team can use Angular's builder API to extend the build process without ejecting. Custom build steps (e.g., generating OpenAPI clients) are handled via npm scripts that run before or after the CLI build.
- **Mitigation (version coupling):** The team uses `ng update` to automate Angular upgrades. This command updates all Angular packages together and applies necessary code migrations. Dependabot is configured to monitor Angular releases and create pull requests for minor and patch updates.

### Alternatives considered
- **Vite (standalone)** — A fast, modern build tool with native ESM support and excellent developer experience. Rejected because Vite requires manual configuration for Angular's AOT compilation, template processing, and lazy loading. Angular CLI's esbuild-based builder already uses Vite under the hood for the dev server, so the team gets Vite's performance benefits without the configuration overhead.
- **webpack (standalone)** — The most mature and flexible JavaScript bundler, with a massive plugin ecosystem. Rejected because webpack is significantly slower than esbuild and requires extensive configuration to support Angular's features. Angular CLI's legacy webpack builder was replaced by the esbuild-based builder for performance reasons.
- **Rollup** — A module bundler optimized for libraries and tree-shaking. Rejected because Rollup is designed for library builds, not application builds. It lacks built-in support for development servers, hot module replacement, and asset processing. Angular CLI's esbuild-based builder provides all these features out of the box.
- **Turbopack** — Next.js's Rust-based bundler, designed as a webpack replacement. Rejected because Turbopack is tightly coupled to Next.js and does not support Angular's AOT compilation or template processing. It is also less mature than esbuild and has limited ecosystem support.

### References
- https://angular.dev/tools/cli
- https://angular.dev/tools/cli/build
- https://github.com/angular/angular-cli
- https://angular.dev/guide/esbuild

