+++
adr = "0036"

[[covers]]
id = "docker-base-images"
version = "alpine"
manifest = "frontend/Dockerfile"
+++

# ADR-0036: Nginx Reverse Proxy — Unprivileged Alpine Image

## Status
Accepted

## Context
Inventra's frontend is a static Angular application (HTML, CSS, JavaScript) that must be served over HTTP/HTTPS. The frontend container requires:
- **Static file serving** — serve `index.html`, JavaScript bundles, and assets with correct MIME types
- **SPA routing** — rewrite all non-file requests to `index.html` for Angular's client-side routing
- **Security headers** — add `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`, and CSP headers
- **Non-root execution** — run as unprivileged user to reduce attack surface

Nginx is a high-performance web server and reverse proxy. The `nginxinc/nginx-unprivileged` image runs as UID 101 (non-root) and listens on port 8080 (HTTP) or 8443 (HTTPS) instead of privileged ports 80/443.

Cross-reference: ADR-0002 (digest pinning) ensures the nginx base image is verified and reproducible.

Source manifest: `frontend/Dockerfile`

## Decision
Use **nginxinc/nginx-unprivileged:alpine** (pinned to SHA-256 digest) as the frontend runtime image.

## Consequences

### Advantages
- **Non-root execution** — runs as UID 101 (`nginx` user), reducing privilege escalation risk if the container is compromised
- **Unprivileged ports** — listens on 8080 (HTTP) or 8443 (HTTPS), avoiding the need for `CAP_NET_BIND_SERVICE` capability
- **Alpine base** — minimal image size (~40 MB) reduces attack surface and download time
- **SPA routing support** — `try_files $uri $uri/ /index.html` rewrites all non-file requests to `index.html` for Angular's client-side routing
- **Security headers** — custom `nginx-security-headers.conf` adds CSP, X-Frame-Options, and other headers to every response

### Disadvantages
- **Port mapping** — unprivileged ports (8080/8443) require port mapping in Docker Compose or Kubernetes to expose standard HTTP/HTTPS ports (80/443)
- **TLS configuration** — HTTPS requires mounting TLS certificates as volumes; self-signed certificates are acceptable for development but require proper CA-signed certificates in production

### Mitigations
- Docker Compose and Kubernetes port mappings handle 80→8080 and 443→8443 translation transparently
- `docker-entrypoint.sh` detects TLS certificate presence and switches between HTTP-only and HTTPS configurations automatically

### Alternatives considered
- **Official nginx image** — Rejected. Runs as root by default, requiring manual user configuration. `nginx-unprivileged` provides non-root execution out of the box.
- **Apache httpd** — Rejected. Nginx is lighter and faster for static file serving. Apache's .htaccess support is not needed for a containerized SPA.
- **Caddy** — Rejected. Caddy provides automatic HTTPS with Let's Encrypt, but Inventra's TLS termination happens at the load balancer (Kubernetes Ingress), not the container. Nginx's manual configuration is sufficient.

### References
- https://hub.docker.com/r/nginxinc/nginx-unprivileged
- https://nginx.org/en/docs/
