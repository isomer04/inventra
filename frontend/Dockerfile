# Build stage
# Node 22 LTS — aligns with CI and documented prerequisites
# Pinned to SHA-256 digest for supply chain integrity
# To update: docker pull node:22-alpine && docker pull nginxinc/nginx-unprivileged:alpine
#            then update the digests below (last verified: 2026-05-23)
FROM node:26-alpine@sha256:e88a35be04478413b7c71c455cd9865de9b9360e1f43456be5951032d7ac1a66 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npm run build:prod

# Runtime stage — serve static files with nginx-unprivileged
# Runs as UID 101 (non-root); listens on 8080 (HTTP) or 8443 (HTTPS).
#
# TLS auto-detection via docker-entrypoint.sh.
# At startup the entrypoint checks for /etc/ssl/inventra/cert.pem:
#   cert present  → HTTPS on 8443 + HTTP→HTTPS redirect on 8080
#   cert absent   → HTTP-only on 8080 with a loud warning (dev/staging only)
FROM nginxinc/nginx-unprivileged:alpine@sha256:18d67281256ded39ff65e010ae4f831be18f19356f83c60bc546492c7eb6dd23

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# limit_req_zone must be at http{} level — load it before server blocks.
# nginx loads /etc/nginx/conf.d/*.conf alphabetically, so 00-* loads first.
COPY nginx-rate-limit.conf /etc/nginx/conf.d/00-rate-limit.conf

# Security headers — included by every location block in both server configs.
COPY nginx-security-headers.conf /etc/nginx/conf.d/security-headers.conf

# Both server configs are staged in a separate directory so the entrypoint
# can copy the correct one to /etc/nginx/conf.d/default.conf at startup.
# They are NOT placed in conf.d directly to avoid nginx loading both.
RUN mkdir -p /etc/nginx/nginx.conf.d
COPY nginx.conf          /etc/nginx/nginx.conf.d/nginx.conf
COPY nginx-http-only.conf /etc/nginx/nginx.conf.d/nginx-http-only.conf

# Entrypoint: detects cert presence and writes the correct default.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh
# Switch to root briefly to make the script executable, then back to nginx user
USER root
RUN chmod +x /docker-entrypoint.sh
USER nginx

EXPOSE 8080 8443

ENTRYPOINT ["/docker-entrypoint.sh"]
