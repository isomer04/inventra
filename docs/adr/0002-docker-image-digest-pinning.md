+++
adr = "0002"
# This ADR documents a process/practice (digest pinning) rather than a specific technology choice.
# It applies to all Docker base images used in the project.
+++ 

# ADR-0002: Pin Docker Base Images to SHA-256 Digests

## Status
Accepted

## Context
Both Dockerfiles (`frontend/Dockerfile`, `backend/Dockerfile`) reference mutable image tags (`node:22-alpine`, `nginxinc/nginx-unprivileged:alpine`, `maven:3-eclipse-temurin-25`, `eclipse-temurin:25-jre`). Mutable tags can be updated by upstream maintainers at any time, silently changing the build environment without any change to committed code.

For a production application this is a supply chain risk: a compromised or accidentally changed upstream image would affect all future builds and deployments.

## Decision
Pin all base images to their current SHA-256 digest. Tags are retained alongside the digest for readability. A comment in each Dockerfile records when the digest was last verified and the command used to retrieve it (`docker pull <image> && docker inspect <image> --format '{{index .RepoDigests 0}}'`).

Digests are updated deliberately (not automatically) as part of planned maintenance. Dependabot automates digest bump PRs via the CI pipeline.

## Consequences
- **Gained:** Build reproducibility and supply chain integrity. A compromised upstream image does not silently affect builds.
- **Cost:** Digest strings must be manually updated when base images are intentionally upgraded. Adds ~2 lines of maintenance per image.
- **Alternatives considered:**
  - Floating tags only (rejected — supply chain risk).
  - Tag-only pinning e.g. `node:22.13.1-alpine` (partially acceptable — tag is immutable by convention for patch versions, but digest is the only cryptographic guarantee).
- **Reference:** https://docs.docker.com/build/building/best-practices/#pin-base-image-versions


