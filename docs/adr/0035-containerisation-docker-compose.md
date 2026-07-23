+++
adr = "0035"

[[covers]]
id = "docker-base-images"
version = "27.0.0"
manifest = "backend/Dockerfile"
+++

# ADR-0035: Containerisation — Docker and Docker Compose

## Status
Accepted

## Context
Inventra requires consistent development and deployment environments across:
- **Local development** — developers need a one-command setup for MySQL, backend, and frontend
- **CI/CD** — GitHub Actions must build and test Docker images to verify production readiness
- **Production deployment** — containers provide process isolation, resource limits, and orchestration compatibility (Kubernetes, Docker Swarm)

Docker provides container runtime and image building. Docker Compose orchestrates multi-container applications with service dependencies, health checks, and environment variable management.

Cross-reference: ADR-0002 (Docker base image digest pinning) ensures supply chain integrity for base images.

Source manifest: `backend/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`, `docker-compose.prod.yml`

## Decision
Use **Docker 27.0.0+** and **Docker Compose 2.29.0+** for containerisation.

Base images:
- Backend build: `maven:3-eclipse-temurin-26` (pinned to SHA-256 digest)
- Backend runtime: `eclipse-temurin:25-jre` (pinned to SHA-256 digest)
- Frontend build: `node:26-alpine` (pinned to SHA-256 digest)
- Frontend runtime: `nginxinc/nginx-unprivileged:alpine` (pinned to SHA-256 digest)

## Consequences

### Advantages
- **Environment parity** — Docker ensures development, CI, and production use identical runtime environments (same JRE version, same Node version, same nginx version)
- **One-command setup** — `docker-compose up` starts MySQL, backend, and frontend with correct dependencies and health checks
- **Multi-stage builds** — Dockerfiles use separate build and runtime stages, reducing final image size (backend: ~200 MB runtime vs. ~800 MB with Maven included)
- **Non-root execution** — backend runs as `inventra` user (UID 1000), frontend runs as `nginx` user (UID 101), reducing attack surface
- **Health checks** — Docker Compose health checks ensure MySQL is ready before starting the backend, and backend is ready before accepting traffic

### Disadvantages
- **Docker dependency** — developers must install Docker Desktop (Windows/Mac) or Docker Engine (Linux); adds ~2 GB disk space and requires virtualization support
- **Build time** — first `docker build` downloads base images and Maven dependencies (~5 minutes); subsequent builds are faster due to layer caching
- **Debugging complexity** — debugging inside containers requires `docker exec` or remote debugging configuration (mitigated by volume mounts for hot reload in development)

### Mitigations
- ADR-0002's digest pinning ensures base images are verified and reproducible
- Docker layer caching reduces rebuild time (Maven dependencies cached in separate layer)
- Development Compose file mounts source code as volumes for hot reload (backend: Spring Boot DevTools, frontend: Angular CLI watch mode)

### Alternatives considered
- **Podman** — Rejected. Podman is Docker-compatible but less widely adopted; Docker Desktop provides better developer experience on Windows/Mac.
- **Vagrant** — Rejected. VM-based approach is heavier than containers (multi-GB disk usage, slower startup). Docker provides better resource efficiency.
- **Manual installation** — Rejected. Requires developers to manually install Java 25, Node 26, MySQL 9, and nginx, with version mismatches across machines. Docker ensures consistency.

### References
- https://docs.docker.com/
- https://docs.docker.com/compose/
