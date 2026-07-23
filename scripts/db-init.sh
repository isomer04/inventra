#!/usr/bin/env bash
# Creates production database identities on the first MySQL volume initialization.
# A shell script is required because the MySQL image does not expand environment
# variables inside raw .sql initialization files.
set -Eeuo pipefail

: "${MYSQL_DATABASE:?MYSQL_DATABASE must be set}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD must be set}"
: "${FLYWAY_PASSWORD:?FLYWAY_PASSWORD must be set}"
: "${APP_PASSWORD:?APP_PASSWORD must be set}"
: "${BACKUP_PASSWORD:?BACKUP_PASSWORD must be set}"

if [[ ! "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "[ERROR] MYSQL_DATABASE may contain only letters, digits, and underscores" >&2
    exit 1
fi

sql_escape() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\'/\'\'}"
    printf '%s' "$value"
}

flyway_password="$(sql_escape "$FLYWAY_PASSWORD")"
app_password="$(sql_escape "$APP_PASSWORD")"
backup_password="$(sql_escape "$BACKUP_PASSWORD")"
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"

mysql --protocol=socket -uroot <<SQL
CREATE USER IF NOT EXISTS 'inventra_flyway'@'%' IDENTIFIED BY '${flyway_password}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO 'inventra_flyway'@'%';
CREATE USER IF NOT EXISTS 'inventra_app'@'%' IDENTIFIED BY '${app_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.* TO 'inventra_app'@'%';
CREATE USER IF NOT EXISTS 'inventra_backup'@'%' IDENTIFIED BY '${backup_password}';
GRANT SELECT, SHOW VIEW ON \`${MYSQL_DATABASE}\`.* TO 'inventra_backup'@'%';
FLUSH PRIVILEGES;
SQL

unset MYSQL_PWD flyway_password app_password backup_password
echo "Database identities created for ${MYSQL_DATABASE}."