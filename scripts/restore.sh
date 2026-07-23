#!/usr/bin/env bash
# Destructive MySQL restore. Blocks ingress, drains application writers, creates
# a safety backup, restores into a clean database, and reopens traffic only after
# the application health check succeeds.
#
# Usage: ./scripts/restore.sh <path-to-dump.sql.gz>
# Required: MYSQL_DATABASE, MYSQL_ROOT_PASSWORD, BACKUP_PASSWORD
# Set SKIP_PRE_RESTORE_BACKUP=true only when the current database is known empty.
set -Eeuo pipefail

DUMP_FILE="${1:-}"
if [ -z "$DUMP_FILE" ] || [ ! -f "$DUMP_FILE" ]; then
    echo "Usage: $0 <path-to-dump.sql.gz>" >&2
    exit 1
fi

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-mysql}"
APP_SERVICE="${APP_SERVICE:-app}"
FRONTEND_SERVICE="${FRONTEND_SERVICE:-frontend}"
DRAIN_TIMEOUT_SECONDS="${DRAIN_TIMEOUT_SECONDS:-30}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-5}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd -- "$(dirname -- "$COMPOSE_FILE")" && pwd)"
MAINTENANCE_DIR="${MAINTENANCE_DIR:-.maintenance}"
if [[ "$MAINTENANCE_DIR" != /* ]]; then
    MAINTENANCE_DIR="$COMPOSE_DIR/$MAINTENANCE_DIR"
fi
MAINTENANCE_MARKER="$MAINTENANCE_DIR/maintenance"
MAINTENANCE_ACTIVE=false
DESTRUCTIVE_PHASE=false

: "${MYSQL_DATABASE:?MYSQL_DATABASE must be set}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD must be set}"

wait_for_app() {
    local attempt
    for ((attempt = 1; attempt <= HEALTH_RETRIES; attempt++)); do
        if docker compose -f "$COMPOSE_FILE" exec -T "$APP_SERVICE" \
            curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
            return 0
        fi
        sleep "$HEALTH_INTERVAL_SECONDS"
    done
    return 1
}

cleanup() {
    local status=$?
    trap - EXIT INT TERM
    if [ "$status" -eq 0 ] || [ "$MAINTENANCE_ACTIVE" != "true" ]; then
        exit "$status"
    fi

    echo "[ERROR] Restore failed; running recovery cleanup." >&2
    if [ "$DESTRUCTIVE_PHASE" = "true" ]; then
        docker compose -f "$COMPOSE_FILE" stop -t "$DRAIN_TIMEOUT_SECONDS" "$APP_SERVICE" || true
        echo "[ERROR] Maintenance remains enabled and the app remains stopped; repair and validate before removing $MAINTENANCE_MARKER." >&2
    else
        if docker compose -f "$COMPOSE_FILE" up -d "$APP_SERVICE" && wait_for_app; then
            rm -f "$MAINTENANCE_MARKER"
            echo "Pre-restore failure occurred before database recreation; app recovered and maintenance was disabled." >&2
        else
            echo "[ERROR] App recovery failed; maintenance remains enabled at $MAINTENANCE_MARKER." >&2
        fi
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

if [[ ! "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "[ERROR] MYSQL_DATABASE may contain only letters, digits, and underscores" >&2
    exit 1
fi

if ! gzip -t "$DUMP_FILE"; then
    echo "[ERROR] Backup archive failed gzip integrity validation" >&2
    exit 1
fi

read -r -p "This will DROP and recreate '$MYSQL_DATABASE'. Type the database name to continue: " CONFIRM
if [ "$CONFIRM" != "$MYSQL_DATABASE" ]; then
    echo "Aborted."
    exit 1
fi

mkdir -p "$MAINTENANCE_DIR"
printf 'restore started %s\n' "$(date -u +%FT%TZ)" > "$MAINTENANCE_MARKER.tmp"
mv -f "$MAINTENANCE_MARKER.tmp" "$MAINTENANCE_MARKER"
MAINTENANCE_ACTIVE=true

docker compose -f "$COMPOSE_FILE" exec -T "$FRONTEND_SERVICE" \
    test -f /var/run/inventra/maintenance
echo "[$(date -u +%FT%TZ)] Maintenance enabled; draining application writers"
docker compose -f "$COMPOSE_FILE" stop -t "$DRAIN_TIMEOUT_SECONDS" "$APP_SERVICE"

if [ "${SKIP_PRE_RESTORE_BACKUP:-false}" != "true" ]; then
    : "${BACKUP_PASSWORD:?BACKUP_PASSWORD is required for the pre-restore safety backup}"
    echo "[$(date -u +%FT%TZ)] Creating pre-restore safety backup"
    bash "$SCRIPT_DIR/backup.sh"
fi

DESTRUCTIVE_PHASE=true
echo "[$(date -u +%FT%TZ)] Recreating database '$MYSQL_DATABASE'"
docker compose -f "$COMPOSE_FILE" exec -T \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$COMPOSE_SERVICE" \
    mysql -uroot -e "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo "[$(date -u +%FT%TZ)] Restoring $DUMP_FILE -> $MYSQL_DATABASE"
gunzip -c "$DUMP_FILE" | docker compose -f "$COMPOSE_FILE" exec -T \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$COMPOSE_SERVICE" \
    mysql -uroot "$MYSQL_DATABASE"

echo "[$(date -u +%FT%TZ)] Starting and validating application"
docker compose -f "$COMPOSE_FILE" up -d "$APP_SERVICE"
if ! wait_for_app; then
    echo "[ERROR] Application did not become healthy after restore" >&2
    exit 1
fi

rm -f "$MAINTENANCE_MARKER"
MAINTENANCE_ACTIVE=false
echo "[$(date -u +%FT%TZ)] Restore validated; maintenance disabled and traffic reopened."
