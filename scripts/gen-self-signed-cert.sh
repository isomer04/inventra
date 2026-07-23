#!/usr/bin/env bash
# gen-self-signed-cert.sh — Generate a self-signed TLS certificate for
# local development and staging testing of the HTTPS path.
#
# ⚠️  WARNING: Self-signed certificates are NOT trusted by browsers and are
#     NOT suitable for production. Use Let's Encrypt or a commercial CA for
#     production deployments.
#
# Usage:
#     ./scripts/gen-self-signed-cert.sh [output-dir] [domain]
#
# Defaults:
#     output-dir  /etc/ssl/inventra
#     domain      localhost
#
# After running this script:
#     1. Ensure TLS_CERT_DIR in .env points to the output directory.
#     2. Restart the frontend container: docker compose restart frontend
#     3. Accept the browser security warning (expected for self-signed certs).
#
# For production, use Let's Encrypt instead:
#     certbot certonly --standalone -d yourdomain.com
#     cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem /etc/ssl/inventra/cert.pem
#     cp /etc/letsencrypt/live/yourdomain.com/privkey.pem   /etc/ssl/inventra/key.pem

set -euo pipefail

OUTPUT_DIR="${1:-/etc/ssl/inventra}"
DOMAIN="${2:-localhost}"
DAYS=365

echo ""
echo "⚠️  SELF-SIGNED CERTIFICATE — FOR DEVELOPMENT/STAGING ONLY"
echo "   Not trusted by browsers. Not suitable for production."
echo ""
echo "Generating certificate for domain: $DOMAIN"
echo "Output directory:                  $OUTPUT_DIR"
echo "Validity:                          $DAYS days"
echo ""

mkdir -p "$OUTPUT_DIR"

openssl req \
    -x509 \
    -newkey rsa:4096 \
    -keyout "$OUTPUT_DIR/key.pem" \
    -out    "$OUTPUT_DIR/cert.pem" \
    -days   "$DAYS" \
    -nodes \
    -subj   "/CN=$DOMAIN/O=Inventra Dev/C=US" \
    -addext "subjectAltName=DNS:$DOMAIN,DNS:localhost,IP:127.0.0.1"

chmod 600 "$OUTPUT_DIR/key.pem"
chmod 644 "$OUTPUT_DIR/cert.pem"

echo ""
echo "✅ Certificate generated:"
echo "   $OUTPUT_DIR/cert.pem  (certificate)"
echo "   $OUTPUT_DIR/key.pem   (private key — keep this secret)"
echo ""
echo "Next steps:"
echo "  1. Ensure TLS_CERT_DIR=$OUTPUT_DIR is set in your .env"
echo "  2. Restart the frontend: docker compose -f docker-compose.prod.yml restart frontend"
echo "  3. Open https://$DOMAIN — accept the browser security warning"
echo ""
echo "To verify the certificate:"
echo "  openssl x509 -in $OUTPUT_DIR/cert.pem -text -noout | grep -A2 'Subject\|Validity\|SAN'"
echo ""
