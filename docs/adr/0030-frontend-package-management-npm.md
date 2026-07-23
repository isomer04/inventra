+++
adr = "0030"

[[covers]]
id = "prettier"
version = "^3.8.1"
manifest = "frontend/package.json"
+++

# ADR-0030: Frontend Package Management — npm

## Status
Accepted

## Context
Inventra's frontend requires a package manager to handle dependency installation, version resolution, script execution, and lockfile management. The frontend codebase depends on Angular 21, TypeScript, ESLint, Vitest, and numerous other npm packages (see `frontend/package.json`). Without a reliable package manager, the team cannot:

- **Install dependencies** consistently across development machines, CI/CD pipelines, and production builds
- **Resolve version conflicts** when multiple packages depend on different versions of the same transitive dependency
- **Execute build scripts** for development servers, production builds, linting, and testing
- **Lock dependency versions** to ensure reproducible builds and prevent "works on my machine" issues
- **Manage workspace structure** if the project grows to include multiple frontend packages (e.g., shared component library, admin dashboard, customer portal)

The package manager must provide:

- **Deterministic installs:** Given the same `package.json` and lockfile, produce identical `node_modules` trees across all environments
- **Performance:** Fast dependency installation and resolution, especially in CI/CD pipelines where dependencies are installed from scratch
- **Security:** Audit dependencies for known vulnerabilities and provide mechanisms to override vulnerable transitive dependencies
- **Ecosystem compatibility:** Work seamlessly with Angular CLI, Vitest, ESLint, and other frontend tooling that expects npm-compatible package management
- **Script execution:** Run npm scripts defined in `package.json` (e.g., `npm run build`, `npm run test`, `npm run lint`)

npm version `11.6.2` is the latest stable release of the npm package manager, providing improved performance, enhanced security auditing, and better workspace support compared to earlier versions. npm is the default package manager for Node.js and has the largest ecosystem of JavaScript packages (over 2 million packages on the npm registry).

The version is declared in `frontend/package.json` using the `packageManager` field:

```json
"packageManager": "npm@11.6.2"
```

This field is part of the Corepack specification, which allows projects to declare their preferred package manager and version. When Corepack is enabled, it automatically downloads and uses the specified npm version, ensuring all developers and CI/CD pipelines use the same package manager version.

Source manifest: `frontend/package.json`

## Decision
Use **npm** version `11.6.2` as the package manager for Inventra's frontend codebase.

Source manifest: `frontend/package.json`, `packageManager` field.

## Consequences
- **Gained (ecosystem compatibility):** npm is the default package manager for Node.js and has universal support across the JavaScript ecosystem. All frontend tooling (Angular CLI, Vitest, ESLint, Prettier) expects npm-compatible package management, ensuring seamless integration without configuration workarounds.
- **Gained (deterministic installs):** npm's `package-lock.json` lockfile records the exact version and integrity hash of every installed package, including transitive dependencies. This ensures that `npm ci` (used in CI/CD pipelines) produces identical `node_modules` trees across all environments, preventing "works on my machine" issues.
- **Gained (security auditing):** npm provides built-in security auditing via `npm audit`, which scans dependencies for known vulnerabilities from the GitHub Advisory Database. The `overrides` field in `package.json` allows forcing specific versions of transitive dependencies to mitigate vulnerabilities without waiting for upstream packages to update.
- **Gained (script execution):** npm's script runner (`npm run <script>`) executes commands defined in `package.json`, providing a consistent interface for development tasks. Scripts like `npm run build`, `npm run test`, and `npm run lint` work identically across all developer machines and CI/CD pipelines.
- **Gained (version pinning):** The `packageManager` field in `package.json` pins the npm version to `11.6.2`, ensuring all developers and CI/CD pipelines use the same package manager version. This prevents subtle differences in dependency resolution or lockfile format that can occur when different npm versions are used.
- **Gained (workspace support):** npm 11 includes improved workspace support for monorepo structures. If Inventra's frontend grows to include multiple packages (e.g., shared component library, admin dashboard, customer portal), npm workspaces can manage them within a single repository without requiring a separate monorepo tool.
- **Cost (installation speed):** npm is slower than alternative package managers like pnpm or Yarn Berry, especially for large dependency trees. Installing Inventra's frontend dependencies from scratch takes approximately 30-60 seconds on a typical developer machine, compared to 15-30 seconds with pnpm. This impacts CI/CD pipeline speed and developer onboarding time.
- **Cost (disk space usage):** npm installs a separate copy of each dependency in every project's `node_modules` directory, even if the same package is used across multiple projects. This consumes more disk space than pnpm's content-addressable store, which deduplicates packages globally. For a developer working on multiple Node.js projects, this can consume several gigabytes of disk space.
- **Cost (lockfile conflicts):** `package-lock.json` is a large JSON file that frequently causes merge conflicts when multiple developers add or update dependencies simultaneously. Resolving these conflicts requires understanding npm's lockfile format and manually merging changes, which can be time-consuming and error-prone.
- **Mitigation (installation speed):** CI/CD pipelines cache `node_modules` between builds, reducing installation time to a few seconds for unchanged dependencies. Developers can use `npm ci` instead of `npm install` for faster, deterministic installs. The team can also consider enabling npm's experimental `--prefer-offline` flag to prioritize cached packages.
- **Mitigation (disk space):** Developers can periodically run `npm cache clean --force` to remove unused cached packages. If disk space becomes a critical issue, the team can reevaluate package manager choice in favor of pnpm, which uses a global content-addressable store.
- **Mitigation (lockfile conflicts):** The team follows a "rebase before merge" workflow to minimize concurrent changes to `package-lock.json`. When conflicts occur, developers regenerate the lockfile by deleting it and running `npm install` rather than manually merging JSON. Code reviews enforce that lockfile changes match `package.json` changes.

### Alternatives considered
- **pnpm** — A fast, disk-efficient package manager that uses a content-addressable store to deduplicate packages globally. pnpm is 2-3x faster than npm for cold installs and uses significantly less disk space. However, pnpm's symlink-based `node_modules` structure can cause compatibility issues with some build tools and IDEs that expect npm's flat structure. Rejected because npm's ecosystem compatibility and team familiarity outweigh pnpm's performance benefits for Inventra's current scale.
- **Yarn Classic (v1)** — The original Yarn package manager, offering faster installs and better lockfile handling than npm 6. However, Yarn Classic is in maintenance mode (no new features), and the Yarn team recommends migrating to Yarn Berry (v2+). Rejected because it is deprecated and offers no advantages over npm 11.
- **Yarn Berry (v2+)** — The modern version of Yarn, featuring Plug'n'Play (PnP) mode that eliminates `node_modules` entirely. PnP improves install speed and reduces disk usage but requires significant configuration changes and has poor compatibility with many tools (Angular CLI, Vitest, ESLint) that expect `node_modules`. Rejected due to ecosystem compatibility concerns and the team's lack of experience with PnP.
- **Bun** — A modern JavaScript runtime and package manager written in Zig, offering extremely fast installs (10x faster than npm). However, Bun is still in early development (v1.x), has limited Windows support, and lacks the maturity and ecosystem compatibility of npm. Rejected because stability and ecosystem compatibility are more important than raw performance for Inventra's production codebase.

### References
- https://docs.npmjs.com/
- https://docs.npmjs.com/cli/v11
- https://github.com/npm/cli
- https://nodejs.org/api/corepack.html
- https://docs.npmjs.com/cli/v11/configuring-npm/package-lock-json
