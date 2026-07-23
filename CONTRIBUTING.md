# Contributing to Inventra

Contributions are welcome. Please read this guide before opening a PR.

---

## Table of Contents
- [Prerequisites](#prerequisites)
- [Local Development Setup](#local-development-setup)
- [Branch Naming](#branch-naming)
- [Commit Messages](#commit-messages)
- [Running Tests](#running-tests)
- [Opening a Pull Request](#opening-a-pull-request)
- [Reporting Security Issues](#reporting-security-issues)

---

## Prerequisites

| Tool | Version |
|------|---------|
| Docker + Docker Compose | Latest stable |
| Java | 25 |
| Maven | 3.9+ |
| Node.js | 22+ |
| npm | 11+ |

---

## Local Development Setup

```bash
# 1. Clone and enter the repo
git clone https://github.com/isomer04/inventra.git
cd inventra

# 2. Copy the env template and set JWT_SECRET
cp .env.example .env
# Edit .env — generate a secret with: openssl rand -base64 64

# 3. Start the dev stack (MySQL + backend)
docker compose up

# 4. Start the frontend dev server (separate terminal)
cd frontend
npm install
npm start
# App: http://localhost:4200
```

---

## Branch Naming

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<short-description>` | `feature/order-export` |
| Bug fix | `fix/<short-description>` | `fix/inventory-race-condition` |
| Chore | `chore/<description>` | `chore/update-deps` |
| Docs | `docs/<description>` | `docs/adr-jwt-rotation` |

**Rules:**
- Always cut from an up-to-date `main`: `git checkout main && git pull --ff-only`
- Never commit directly to `main`
- One concern per branch — don't mix features with fixes

---

## Commit Messages

This project follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).

```
<type>(<scope>): <short description>

[optional body]

[optional footer: BREAKING CHANGE, Fixes #issue]
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`

**Examples:**
```
feat(orders): add order export to CSV
fix(auth): prevent JWT reuse after logout
chore(deps): bump Spring Boot to 4.0.7
```

**Do not** use: `wip`, `asdf`, `fix fix`, `final`, `temp`, `update stuff`

---

## Running Tests

### Backend
```bash
cd backend
mvn test                          # unit + integration (Testcontainers)
mvn verify                        # + SpotBugs SAST
mvn -P security-scan verify       # + OWASP Dependency-Check
```

### Frontend
```bash
cd frontend
npm test                          # Vitest (watch mode)
npm run lint                      # ESLint
```

---

## Opening a Pull Request

1. Push your branch and open a PR against `main`
2. Fill in the PR template completely — incomplete PRs will not be reviewed
3. Ensure all checks pass (lint, typecheck, tests, build)
4. Link any related issue (e.g. `Closes #42`)
5. Wait for review — do not merge your own PRs

---

## Reporting Security Issues

**Do not open a public issue for security vulnerabilities.**
See [SECURITY.md](SECURITY.md) for the private disclosure process.
