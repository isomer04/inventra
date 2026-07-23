#!/usr/bin/env bash
# MySQL backups for the Inventra production stack. Uses the dedicated read-only
# inventra_backup account created by db-init.sh, writes a compressed dump, validates
# it, and prunes old local backups.
#
# Usage:
#     ./scripts/backup.sh
#
# Required environment (typically read from .env):
#     MYSQL_DATABASE, BACKUP_PASSWORD
#
# Optional environment:
#     BACKUP_USER      defaults to inventra_backup
#     COMPOSE_FILE     defaults to docker-compose.prod.yml
#     COMPOSE_SERVICE  defaults to mysql
#     BACKUP_DIR       defaults to /var/backups/inventra
#     RETENTION_DAYS   defaults to 14
#
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-mysql}"
BACKUP_USER="${BACKUP_USER:-inventra_backup}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/inventra}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

: "${MYSQL_DATABASE:?MYSQL_DATABASE must be set}"
: "${BACKUP_PASSWORD:?BACKUP_PASSWORD must be set}"

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_FILE="$BACKUP_DIR/inventra-$TIMESTAMP.sql.gz"

echo "[$(date -u +%FT%TZ)] Starting backup -> $DUMP_FILE"

# --single-transaction: consistent dump without table locks (InnoDB)
# --quick: stream rows, do not buffer in memory
# --skip-triggers: dump table definitions and rows only; migrations own triggers
# --no-tablespaces: avoids requiring the global PROCESS privilege
# --set-gtid-purged=OFF: avoid GTID metadata that complicates restore on a fresh server
docker compose -f "$COMPOSE_FILE" exec -T \
    -e MYSQL_PWD="$BACKUP_PASSWORD" "$COMPOSE_SERVICE" \
    mysqldump \
        --single-transaction \
        --quick \
        --skip-lock-tables \
        --skip-triggers \
        --no-tablespaces \
        --set-gtid-purged=OFF \
        -u "$BACKUP_USER" \
        "$MYSQL_DATABASE" \
    | gzip > "$DUMP_FILE"

# Verify the dump is non-trivial (at least 1 KB after gzip)
size=$(stat -c%s "$DUMP_FILE" 2>/dev/null || stat -f%z "$DUMP_FILE")
if [ "$size" -lt 1024 ]; then
    echo "[ERROR] Dump file is suspiciously small ($size bytes). Aborting." >&2
    rm -f "$DUMP_FILE"
    exit 1
fi

echo "[$(date -u +%FT%TZ)] Backup complete: $(du -h "$DUMP_FILE" | cut -f1)"

echo "[$(date -u +%FT%TZ)] Pruning dumps older than $RETENTION_DAYS days"
find "$BACKUP_DIR" -name 'inventra-*.sql.gz' -type f -mtime +"$RETENTION_DAYS" -print -delete

echo "[$(date -u +%FT%TZ)] Done."
