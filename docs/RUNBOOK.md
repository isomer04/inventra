# Inventra Operational Runbook

Operational procedures for the production deployment.

---

## TLS Activation

TLS is the only remaining blocker for production launch. The nginx container
auto-detects the certificate at startup — no config file editing required.

### Production (Let's Encrypt — recommended)

```bash
# 1. Install certbot on the host
apt-get install -y certbot   # Debian/Ubuntu
# or: brew install certbot   # macOS

# 2. Obtain a certificate (stop the stack first so port 80 is free)
docker compose -f docker-compose.prod.yml stop frontend
certbot certonly --standalone -d yourdomain.com

# 3. Copy the cert to the standard location
mkdir -p /etc/ssl/inventra
cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem /etc/ssl/inventra/cert.pem
cp /etc/letsencrypt/live/yourdomain.com/privkey.pem   /etc/ssl/inventra/key.pem
chmod 600 /etc/ssl/inventra/key.pem

# 4. Set TLS_CERT_DIR in .env (default is /etc/ssl/inventra — no change needed)

# 5. Start the stack — nginx detects the cert and enables HTTPS automatically
docker compose -f docker-compose.prod.yml up -d

# 6. Verify HTTPS is working
curl -I https://yourdomain.com/index.html

# 7. Submit to HSTS preload list (one-time, after confirming HTTPS works)
#    https://hstspreload.org
```

### Staging / local testing (self-signed cert)

```bash
# Generate a self-signed cert (browser will show a security warning — expected)
./scripts/gen-self-signed-cert.sh /etc/ssl/inventra localhost

# Restart the frontend container
docker compose -f docker-compose.prod.yml restart frontend

# Verify — accept the browser security warning
curl -k https://localhost/index.html
```

### Let's Encrypt renewal

Let's Encrypt certificates expire after 90 days. Set up auto-renewal:

```bash
# Test renewal (dry run)
certbot renew --dry-run

# Add to crontab for automatic renewal twice daily (standard recommendation)
# 0 0,12 * * * certbot renew --quiet && \
#   cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem /etc/ssl/inventra/cert.pem && \
#   cp /etc/letsencrypt/live/yourdomain.com/privkey.pem /etc/ssl/inventra/key.pem && \
#   docker compose -f /opt/inventra/docker-compose.prod.yml restart frontend
```

### Verifying TLS mode

Check the container startup logs to confirm which mode is active:

```bash
docker compose -f docker-compose.prod.yml logs frontend | head -20
# ✅ TLS ENABLED — serving HTTPS on port 8443   → cert found, HTTPS active
# ⚠️  WARNING: TLS NOT ENABLED                  → cert missing, HTTP-only
```

---

## Backups

### What gets backed up
- The full MySQL `inventra` database, including all tenant data, refresh tokens, and audit history.
- Schema, stored routines, and triggers.

### What does NOT get backed up
- Application logs (consider shipping to an external aggregator).
- Container images (rebuild from git).
- `.env` (store separately in a secrets vault).

### Backup script

`scripts/backup.sh` runs `mysqldump` against the live container, writes a gzipped
timestamped dump to `BACKUP_DIR`, and prunes dumps older than `RETENTION_DAYS`.

```bash
# One-off backup (loads .env automatically if present)
set -a; source .env; set +a
./scripts/backup.sh
```

### Scheduling daily backups

Add to crontab on the host running docker-compose:

```cron
# Daily at 02:30 UTC
30 2 * * * cd /opt/inventra && set -a && source .env && set +a && ./scripts/backup.sh >> /var/log/inventra-backup.log 2>&1
```

### Off-host storage (required for real DR)

Local backups protect against bad migrations and accidental deletes. They do NOT
protect against host loss. Configure one of the off-host options below by editing
the `REMOTE_DEST` block in `scripts/backup.sh`:

| Option | Setup | Cost |
|---|---|---|
| **rclone to S3** | `rclone config` → set `REMOTE_DEST=s3:your-bucket/inventra-backups` | $1–5/month for typical DBs |
| **rsync to second host** | SSH key auth → set `REMOTE_DEST=user@backup-host:/var/backups/inventra` | Free if you have a second host |
| **borgbackup / restic** | Encrypted, deduplicated, can target S3 or B2 | Recommended for compliance-sensitive deployments |

Test the off-host upload by running the backup once and verifying the file lands
at the destination before enabling cron.

### Restore

```bash
set -a; source .env; set +a
./scripts/restore.sh /var/backups/inventra/inventra-20260517T023000Z.sql.gz
```

The restore script enters nginx maintenance mode, gracefully drains the application,
takes its safety backup, reloads the database, restarts the application, and keeps
traffic blocked until the application health check succeeds. It prompts for the
exact database name before destroying data. If restoration or validation fails
after database recreation, maintenance remains enabled and the app is stopped for
manual recovery.

### Restore drill (do this monthly)

A backup that has never been restored is not a backup. Once a month:

1. Spin up a separate compose stack (`docker compose -f docker-compose.prod.yml -p inventra-test up -d`)
2. Run `./scripts/restore.sh` against the test stack with the latest dump
3. Hit `/actuator/health`, log in as a known tenant, verify counts on key tables
4. Tear the test stack down

Document the date of the last successful restore drill below:

| Date | Dump tested | Result | Notes |
|---|---|---|---|
| _to be filled_ | | | |

---

## Disaster Recovery

### Scenario 1: Bad migration corrupts data

1. Restore from the most recent pre-migration backup: `./scripts/restore.sh <dump>`; the script blocks ingress and drains the app.
2. If the restored version is incompatible, keep maintenance enabled, roll back with `git checkout <previous-tag>`, and rebuild.
3. Start and validate the rolled-back app, then remove the maintenance marker only after validation succeeds.

### Scenario 2: Host loss

1. Provision a fresh host with Docker installed.
2. Clone the repo, copy `.env` from your secrets vault.
3. Pull the latest off-host backup: `rclone copy s3:your-bucket/inventra-backups/<latest>.sql.gz .`
4. Start the stack: `docker compose -f docker-compose.prod.yml up -d`
5. Wait for MySQL to be healthy: `docker compose ps`
6. Restore: `./scripts/restore.sh <dump>`

### Scenario 3: Compromised JWT secret

1. Generate a new secret: `openssl rand -base64 64`
2. Update `JWT_SECRET` in `.env`
3. Restart the app: `docker compose -f docker-compose.prod.yml restart app`
4. **All existing access tokens become invalid immediately** (signature mismatch) — users will be forced to log in again.
5. **All existing refresh tokens become invalid** — the SHA-256 hash of any submitted token will not match what is stored.

### Scenario 4: Compromised DB password

1. Generate a new password.
2. Update the user's password in MySQL: `ALTER USER 'inventra'@'%' IDENTIFIED BY '<new>';`
3. Update `MYSQL_PASSWORD` and `DB_PASSWORD` in `.env`.
4. Restart the stack: `docker compose -f docker-compose.prod.yml restart`

---

## Secrets Rotation

| Secret | Rotation cadence | Procedure |
|---|---|---|
| `JWT_SECRET` | Every 90 days, or immediately on compromise | See DR Scenario 3 |
| `MYSQL_PASSWORD` | Every 180 days | See DR Scenario 4 |
| `MYSQL_ROOT_PASSWORD` | Every 180 days | `ALTER USER 'root'@'%' IDENTIFIED BY '<new>';` then update `.env` and restart |
| TLS certificate | Per cert authority | If using Let's Encrypt, automate via certbot |

Rotation rule: never rotate two secrets at once. Verify the system is healthy
between rotations.

---

## Health Monitoring

| Endpoint | Expected response | Owner |
|---|---|---|
| `GET /actuator/health` | `{"status":"UP"}` | App |
| MySQL | `mysqladmin ping` succeeds | Compose healthcheck |
| Frontend | `curl http://host/index.html` returns HTML | nginx |

Wire each of these to your uptime monitor (UptimeRobot, BetterStack, etc.).

---

## Common Issues

### Refresh token not working / users getting logged out
- Check JWT_SECRET hasn't been rotated without notifying users
- Check `refresh_token` table — `consumeIfActive` requires `revoked = false AND expires_at > NOW()`
- Default refresh expiry is 7 days — old refresh tokens will be expired

### Order creation returning 500
- This was a known bug — fixed in `OrderController` to use `User.getId()` instead of `getUsername()`. If it returns, check that `@AuthenticationPrincipal` resolves to the `User` entity, not a wrapping `UserDetails`.

### "Migration mismatch" on startup
- V6 migration was intentionally removed. `application-prod.yml` sets `ignore-migration-patterns: "*:Missing"`. If a different version is missing, investigate before bypassing.
