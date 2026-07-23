+++
# Required fields — replace all placeholder values before committing.
# adr: zero-padded four-digit identifier (e.g. "0005")
adr = "NNNN"

# Add one [[covers]] block per technology this ADR documents.
# id format:
#   Maven artifact  → "groupId:artifactId"  (e.g. "org.springframework.boot:spring-boot-starter-web")
#   npm package     → package name as declared in package.json  (e.g. "vitest")
#   Docker image    → image name without digest  (e.g. "eclipse-temurin")
#   GitHub Action   → action reference  (e.g. "actions/checkout")
#   Compose service → image name  (e.g. "mysql")
# version: copy the version string exactly as it appears in the source manifest.
# manifest: repository-relative path to the manifest file  (e.g. "backend/pom.xml").
[[covers]]
id = "<canonical-id>"
version = "<version-string-from-manifest>"
manifest = "<relative-path-to-manifest>"
+++

# ADR-NNNN: <Title>

## Status
Accepted

## Context
<!-- Describe the problem, need, or requirement this technology addresses.
     State the source manifest and the version string exactly as declared there. -->

## Decision
<!-- Name the chosen technology and its version.
     Source manifest: <relative path, e.g. backend/pom.xml> -->

## Consequences
<!-- Advantages: at least one specific to Inventra's use. -->
<!-- Disadvantages / costs / risks: at least one specific to Inventra's use. -->
<!-- Mitigations: describe any applied or planned mitigations. -->

### Alternatives considered
- **<Alternative>** — <reason rejected, compared against the chosen technology>

### References
- <https://official-docs-url>
