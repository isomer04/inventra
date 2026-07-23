+++
adr = "0022"

[[covers]]
id = "prettier"
version = "^3.8.1"
manifest = "frontend/package.json"
+++

# ADR-0022: Code Formatting — Prettier

## Status
Accepted

## Context
Inventra's frontend codebase requires automated code formatting to maintain consistent style across the development team. Without automated formatting, code reviews devolve into debates about whitespace, indentation, line length, and quote styles — subjective preferences that distract from substantive feedback about logic, architecture, and correctness.

Manual formatting is error-prone and time-consuming. Developers spend mental energy on formatting decisions (should this array span multiple lines? where should this line break?) rather than solving business problems. Inconsistent formatting makes diffs harder to read, obscures meaningful changes, and creates merge conflicts when different developers format the same code differently.

The formatting solution must:

- **Enforce consistency:** Apply a single, opinionated style across all TypeScript, JavaScript, HTML, CSS, and JSON files in the frontend codebase.
- **Integrate with development workflow:** Run automatically on file save in IDEs, via npm scripts (`npm run format`), and in pre-commit hooks to prevent unformatted code from entering the repository.
- **Complement ESLint:** Work alongside ESLint (see [ADR-0021](0021-frontend-linting-eslint.md)) without conflicting rules. ESLint handles error detection and code quality; Prettier handles formatting.
- **Support Angular templates:** Format HTML templates embedded in Angular components, including Angular-specific syntax (property bindings, event handlers, structural directives).
- **Minimize configuration:** Provide sensible defaults that work for most projects, reducing the need for team debates about formatting rules.

Prettier version `^3.8.1` is the latest major version, providing an opinionated code formatter with support for TypeScript, JavaScript, HTML, CSS, JSON, and Markdown. The caret (`^`) range allows minor and patch updates within version 3, ensuring bug fixes and new language feature support without breaking changes.

Prettier is declared in `frontend/package.json` as a development dependency. It formats code automatically, removing all original styling and ensuring consistent output regardless of how code was originally written.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Decision
Use **Prettier** version `^3.8.1` for automated code formatting across the frontend codebase.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Consequences
- **Gained (zero formatting debates):** Prettier is opinionated and non-configurable by design (with minimal configuration options). This eliminates team debates about formatting preferences — there is one correct format, and Prettier enforces it. Code reviews focus on logic and architecture rather than style.
- **Gained (consistent codebase):** All code follows the same formatting rules, regardless of who wrote it or which IDE they used. This makes the codebase easier to navigate, reduces cognitive load when switching between files, and improves readability for new team members.
- **Gained (automatic formatting):** Prettier integrates with VS Code, WebStorm, and other IDEs to format code on save. Developers never manually format code — they write logic, save the file, and Prettier handles the rest. This saves time and mental energy.
- **Gained (cleaner diffs):** Consistent formatting reduces noise in Git diffs. When a developer changes a function's logic, the diff shows only the logical change, not incidental whitespace or indentation adjustments. This makes code reviews faster and more effective.
- **Gained (ESLint integration):** Prettier integrates with ESLint via `eslint-config-prettier`, which disables ESLint's formatting rules that conflict with Prettier. This allows ESLint to focus on error detection (unused variables, type errors) while Prettier handles formatting (indentation, line length).
- **Gained (multi-language support):** Prettier formats TypeScript, JavaScript, HTML, CSS, JSON, and Markdown files using a single tool. This simplifies the toolchain compared to using separate formatters for each language (e.g., `js-beautify` for JavaScript, `html-beautify` for HTML).
- **Cost (opinionated style):** Prettier's formatting choices are non-negotiable. Some developers may dislike specific decisions (e.g., always using semicolons, preferring double quotes, breaking long lines at 80 characters). The team must accept Prettier's style or spend time configuring overrides, which defeats the purpose of an opinionated formatter.
- **Cost (large initial diffs):** Introducing Prettier to an existing codebase requires formatting all files at once, creating a massive Git commit that touches every file. This makes `git blame` less useful for those files (though `git blame --ignore-rev` can skip the formatting commit). For Inventra, this is a non-issue because Prettier is adopted from the start.
- **Cost (build time overhead):** Running Prettier adds time to the development workflow, both in pre-commit hooks and CI/CD pipelines. Formatting the entire frontend codebase can take several seconds. However, this cost is minimal compared to the time saved by eliminating manual formatting.
- **Mitigation (opinionated style):** The team accepts Prettier's defaults with minimal configuration. The only overrides are project-specific requirements (e.g., line length for readability on smaller screens). Developers are encouraged to trust Prettier's decisions rather than debating style.
- **Mitigation (build time):** Prettier caches results between runs, only re-formatting changed files. Pre-commit hooks format only staged files rather than the entire codebase. CI/CD pipelines run formatting checks in parallel with linting and tests to minimize total pipeline time.

### Alternatives considered
- **ESLint (with formatting rules)** — ESLint can enforce formatting rules via plugins like `eslint-plugin-prettier` or built-in rules (`indent`, `quotes`, `semi`). However, ESLint's formatting rules are slower than Prettier, less comprehensive (e.g., no automatic line breaking), and require extensive configuration. ESLint is designed for error detection, not formatting. Rejected because Prettier is faster, more opinionated, and purpose-built for formatting.
- **Biome** — Biome is a modern linting and formatting tool written in Rust, offering faster performance than Prettier. However, Biome's formatting is less mature than Prettier's, with fewer language features supported and less community adoption. Biome's formatting output can differ from Prettier's, making migration difficult for teams already using Prettier. Rejected due to less mature formatting and smaller ecosystem.
- **dprint** — dprint is a fast code formatter written in Rust, offering Prettier-compatible formatting with better performance. However, dprint's ecosystem is smaller than Prettier's, with fewer IDE integrations and less community support. dprint's configuration is more complex than Prettier's, requiring WASM plugins for different languages. Rejected because Prettier's ecosystem and IDE support are more mature.
- **Manual formatting (with style guide)** — The team could enforce formatting manually via code review, using a written style guide (e.g., Airbnb JavaScript Style Guide). Rejected because manual enforcement is inconsistent, time-consuming, and error-prone. Developers spend time debating style in code reviews rather than focusing on logic. Automated formatting eliminates these debates entirely.

### References
- https://prettier.io/
- https://prettier.io/docs/en/integrating-with-linters.html
- https://github.com/prettier/prettier
- https://prettier.io/docs/en/options.html
