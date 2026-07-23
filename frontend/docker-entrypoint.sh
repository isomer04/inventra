#!/bin/sh
# docker-entrypoint.sh — TLS auto-detection for the nginx frontend container.
#
# Switches nginx between HTTPS mode and HTTP-only mode based on
# whether a TLS certificate is present at /etc/ssl/inventra/cert.pem.
#
# HTTPS mode  (cert present):
#   - Copies nginx.conf (HTTPS + redirect) to /etc/nginx/conf.d/default.conf
#   - Logs a green confirmation
#
# HTTP-only mode (cert absent):
#   - Copies nginx-http-only.conf to /etc/nginx/conf.d/default.conf
#   - Logs a loud WARNING — not suitable for production
#
# To activate HTTPS:
#   1. Place cert.pem + key.pem in /etc/ssl/inventra/ on the host
#   2. Ensure the volume is mounted in docker-compose.prod.yml (already configured)
#   3. Restart the container: docker compose restart frontend
#
# To generate a self-signed cert for local/staging testing:
#   ./scripts/gen-self-signed-cert.sh

set -e

CERT_FILE="/etc/ssl/inventra/cert.pem"
KEY_FILE="/etc/ssl/inventra/key.pem"
CONF_DIR="/etc/nginx/conf.d"

if [ -f "$CERT_FILE" ] && [ -f "$KEY_FILE" ]; then
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  ✅  TLS ENABLED — serving HTTPS on port 8443               ║"
    echo "║      HTTP on port 8080 redirects to HTTPS                   ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    cp /etc/nginx/nginx.conf.d/nginx.conf "$CONF_DIR/default.conf"
else
    if [ "${REQUIRE_TLS:-false}" = "true" ]; then
        echo "[FATAL] TLS is required but cert.pem or key.pem is missing." >&2
        echo "        Expected: $CERT_FILE and $KEY_FILE" >&2
        exit 1
    fi

    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  ⚠️   WARNING: TLS NOT ENABLED — serving plain HTTP          ║"
    echo "║      No certificate found at $CERT_FILE"
    echo "║                                                              ║"
    echo "║  JWTs, passwords, and all user data are transmitted         ║"
    echo "║  in plaintext. DO NOT use this in production.               ║"
    echo "║                                                              ║"
    echo "║  To enable TLS:                                             ║"
    echo "║    1. Run: ./scripts/gen-self-signed-cert.sh (staging)      ║"
    echo "║       or:  certbot certonly -d yourdomain.com (production)  ║"
    echo "║    2. Mount /etc/ssl/inventra in docker-compose.prod.yml    ║"
    echo "║    3. Restart: docker compose restart frontend              ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    cp /etc/nginx/nginx.conf.d/nginx-http-only.conf "$CONF_DIR/default.conf"
fi

# Hand off to the official nginx entrypoint
exec nginx -g "daemon off;"
