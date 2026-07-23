+++
adr = "0037"

[[covers]]
id = "github-actions"
version = "v6.0.2"
manifest = ".github/workflows/ci.yml"
+++

# ADR-0037: CI Platform — GitHub Actions

## Status
Accepted

## Context
Inventra requires continuous integration to:
- **Build and test** — run backend unit tests, frontend unit tests, and integration tests on every push and pull request
- **Security scanning** — run SpotBugs (SAST) and OWASP Dependency-Check (SCA) to detect vulnerabilities
- **Docker image validation** — verify backend and frontend Docker images build successfully
- **ADR coverage verification** — ensure all significant technology choices are documented in ADRs

GitHub Actions provides CI/CD workflows integrated with GitHub repositories, with free minutes for public repositories and pay-as-you-go pricing for private repositories.

Source manifest: `.github/workflows/ci.yml`

## Decision
Use **GitHub Actions** as the CI platform, with all actions pinned to specific commit SHAs for supply chain security.

Pinned actions:
- `actions/checkout@v6.0.2` — checkout repository code
- `actions/setup-java@v5.2.0` — install Java 25 with Temurin distribution
- `actions/setup-node@v6.4.0` — install Node 22 with npm cache
- `actions/upload-artifact@v7.0.1` — upload test coverage reports
- `actions/setup-python@v5.3.0` — install Python 3.11 for ADR verification script

## Consequences

### Advantages
- **GitHub integration** — CI status checks appear directly on pull requests; branch protection rules can require passing checks before merge
- **Matrix builds** — GitHub Actions supports matrix builds for testing multiple Java/Node versions (not currently used, but available for future expansion)
- **Artifact storage** — test coverage reports (JaCoCo, Vitest) are uploaded as artifacts and retained for 14 days
- **Action pinning** — all actions are pinned to commit SHAs (not tags) to prevent supply chain attacks via compromised action repositories
- **Dependabot integration** — `.github/dependabot.yml` configures automatic PRs to update action SHAs when new versions are released

### Disadvantages
- **Vendor lock-in** — GitHub Actions workflows use GitHub-specific syntax (YAML with `jobs`, `steps`, `uses`); migrating to GitLab CI or Jenkins requires rewriting workflows
- **Minute limits** — private repositories have monthly minute limits (2,000 minutes/month for free tier); exceeding the limit requires paid plan
- **Action trust** — third-party actions (e.g., `actions/setup-java`) require trust in the action maintainer; SHA pinning mitigates but does not eliminate this risk

### Mitigations
- SHA pinning ensures actions cannot be silently updated to malicious versions
- Dependabot automates action updates with PR review workflow
- Minute usage is monitored via GitHub billing dashboard; CI jobs are optimized to minimize runtime (e.g., OWASP Dependency-Check runs only on main branch pushes)

### Alternatives considered
- **GitLab CI** — Rejected. Inventra uses GitHub for repository hosting; GitLab CI would require mirroring the repository or migrating to GitLab.
- **Jenkins** — Rejected. Self-hosted Jenkins requires infrastructure maintenance (server provisioning, plugin updates, security patches). GitHub Actions provides managed infrastructure.
- **CircleCI** — Rejected. Similar to GitHub Actions but requires separate account and configuration. GitHub Actions provides tighter integration with GitHub repositories.

### References
- https://docs.github.com/en/actions
- https://github.com/actions
