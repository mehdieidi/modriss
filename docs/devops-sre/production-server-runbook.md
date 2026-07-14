# Production Server Deployment and Operations Runbook

This runbook describes how to deploy Varka to a real internet-facing server with a domain name,
TLS, persistent data, monitoring, backups, and ongoing maintenance.

It assumes a first production deployment on one Linux server with Docker Compose. That is a valid
starting point for a small product, but the same operational rules apply later if the system moves
to Kubernetes, managed databases, or managed observability.

## Target Shape

Recommended public surfaces:

| Surface                | Example hostname              | Public?                                 | Notes                                                                |
| ---------------------- | ----------------------------- | --------------------------------------- | -------------------------------------------------------------------- |
| User app               | `https://app.example.com`     | Yes                                     | Primary customer-facing application.                                 |
| Backend API            | `https://api.example.com`     | Yes, restricted by auth and rate limits | Used by browser apps and integrations.                               |
| Admin app              | `https://admin.example.com`   | Limited                                 | Protect with admin auth and preferably VPN, SSO, or IP allowlisting. |
| Public docs or landing | `https://example.com`         | Yes                                     | Optional marketing/docs surface.                                     |
| Grafana                | `https://grafana.example.com` | No by default                           | Expose only behind VPN/SSO/IP allowlist.                             |
| Prometheus             | Internal only                 | No                                      | Do not expose publicly.                                              |
| Loki                   | Internal only                 | No                                      | Do not expose publicly.                                              |
| PostgreSQL             | Internal only                 | No                                      | Never expose directly to the internet.                               |
| Actuator               | Internal only or admin-only   | No public anonymous access              | Health can be public only if it reveals no sensitive detail.         |

For the first production version, prefer Caddy as the public edge proxy and keep backend services on
private Docker networks. Only ports `80` and `443` should be reachable from the internet unless
there is a specific operational reason.

## Phase 1: Preflight Decisions

Before touching the server, decide:

- Domain names: choose the exact hostnames for app, API, admin, and optional docs.
- Deployment model: single server with Docker Compose for the first production deployment.
- Database model: local PostgreSQL volume for early production, or managed PostgreSQL if uptime and
  backups matter immediately.
- Email/auth model: which email account receives the first admin role.
- Backup location: an external object store or backup server, not only a local disk.
- Alert destination: email, Slack, PagerDuty, or another channel that someone actually checks.
- Recovery objective: how much data loss is acceptable and how long recovery may take.

Write these decisions down in an environment-specific deployment note before launch.

## Phase 2: Server Provisioning

Start with a clean Ubuntu LTS server from a reputable provider.

Minimum baseline for a small production deployment:

- 2 vCPU.
- 4 GB RAM.
- 60 GB SSD.
- Daily provider snapshots.
- Static public IPv4 address.

Provisioning checklist:

- Create a non-root deploy user with sudo access.
- Disable password SSH login.
- Use SSH keys only.
- Enable automatic security updates.
- Set the server timezone to UTC.
- Install Docker Engine and Docker Compose plugin from official packages.
- Configure a firewall with only SSH, HTTP, and HTTPS open.
- Restrict SSH to your IP address if practical.
- Configure log rotation for Docker and system logs.
- Add basic host monitoring for disk, memory, CPU, and reboot events.

Example firewall intent:

```bash
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
ufw status
```

Do not publish PostgreSQL, Prometheus, Loki, LocalStack, or internal app ports on the public
interface.

## Phase 3: DNS

Create DNS records before starting TLS.

Example records:

| Type | Name                  | Target                                     |
| ---- | --------------------- | ------------------------------------------ |
| `A`  | `example.com`         | Server IPv4                                |
| `A`  | `app.example.com`     | Server IPv4                                |
| `A`  | `api.example.com`     | Server IPv4                                |
| `A`  | `admin.example.com`   | Server IPv4                                |
| `A`  | `grafana.example.com` | Server IPv4, only if intentionally exposed |

Use a low TTL during launch, such as 300 seconds. After the deployment is stable, raise the TTL if
desired.

Verify DNS from outside the server:

```bash
dig app.example.com
dig api.example.com
dig admin.example.com
```

## Phase 4: Production Configuration

Create a production `.env` on the server. Do not commit it.

Required production changes:

- Generate strong random database passwords.
- Generate strong Grafana credentials if Grafana is deployed.
- Temporarily set `VARKA_ADMIN_BOOTSTRAP_ENABLED=true` and
  `VARKA_ADMIN_BOOTSTRAP_EMAILS` to the initial admin email before first admin login.
- Set `VARKA_ADMIN_BOOTSTRAP_TOKEN` to a long random one-time setup token before first admin login.
- Set `VARKA_ALLOWED_ORIGINS` to only the real HTTPS origins.
- Set all public URLs to `https://...`.
- Configure AI provider keys only through environment variables or a secret manager.
- Disable or remove LocalStack unless it is explicitly needed.
- Set upload and data paths to durable volumes.
- Avoid local default passwords such as `admin`, `varka`, `password`, or `changeme`.

Example origin shape:

```env
VARKA_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com,https://api.example.com
```

Secrets policy:

- Store production secrets outside Git.
- Limit shell history exposure when editing secrets.
- Rotate secrets after accidental exposure.
- Keep a break-glass admin recovery procedure in a private location.

## Phase 5: Production Caddy Routing

Use Caddy for HTTPS and host routing. The local Caddyfile is a starting point, not the final
production policy.

Production Caddy expectations:

- Remove `auto_https off`.
- Listen on real hostnames, not `localhost`.
- Redirect HTTP to HTTPS.
- Proxy only the surfaces intended to be public.
- Keep observability tools private or protected.
- Add stricter security headers after testing each app.
- Add request body limits for upload endpoints if needed.

Example shape:

```caddyfile
app.example.com {
  reverse_proxy frontend:8082
}

api.example.com {
  reverse_proxy backend:8080
}

admin.example.com {
  reverse_proxy admin:8084
}
```

For admin and operational tools, prefer one of these before going public:

- VPN-only access.
- Identity-aware proxy or SSO in front of the route.
- IP allowlisting.
- A separate private network reachable only by operators.

## Phase 6: Compose Production Adjustments

The development Compose stack publishes many ports for convenience. Production should not.

Production Compose changes:

- Publish only Caddy ports `80:80` and `443:443`.
- Remove public port mappings from backend, admin, frontend, PostgreSQL, Prometheus, Loki, Grafana,
  LocalStack, and Dozzle unless there is an explicit reason.
- Put services on private Docker networks.
- Add restart policies.
- Add memory and CPU limits where appropriate.
- Keep persistent named volumes for PostgreSQL, uploads, Grafana, Prometheus, and Loki.
- Do not run build-heavy workflows on the server if CI can build and push images.

Preferred release model:

1. CI builds versioned images.
2. CI runs tests, migration checks, and image scans.
3. Server pulls immutable image tags.
4. Server runs `docker compose up -d`.

Acceptable first deployment model:

1. Pull the repository on the server.
2. Check out a known commit or tag.
3. Build on the server.
4. Run `docker compose up --build -d`.

The first model is better for repeatability and rollback.

## Phase 7: First Deployment

First deployment checklist:

1. Confirm DNS points to the server.
2. Confirm firewall allows `80` and `443`.
3. Create the production `.env`.
4. Configure production Caddy hostnames.
5. Confirm persistent volumes are defined.
6. Run Compose config validation.
7. Start the stack.
8. Confirm Caddy obtains TLS certificates.
9. Confirm backend readiness.
10. Log in as the bootstrap admin.
11. Set `VARKA_ADMIN_BOOTSTRAP_ENABLED=false` and remove bootstrap admin emails and the bootstrap
    token after roles are assigned.
12. Confirm audit logs record admin actions.
13. Confirm metrics and logs are flowing.
14. Create and restore-test the first backup.

Useful commands:

```bash
docker compose config -q
docker compose up -d
docker compose ps
docker compose logs -f caddy backend
curl -I https://app.example.com
curl https://api.example.com/actuator/health/readiness
```

Do not announce the service publicly until login, admin access, backups, and monitoring have all
been verified.

## Phase 8: Database Migrations

Flyway applies schema migrations when the backend starts.

Safe migration process:

1. Create new migrations only with `scripts/flyway-next-migration.py`.
2. Validate migration ordering before release.
3. Review destructive migrations separately.
4. Back up PostgreSQL before deploying migrations to production.
5. Deploy during a low-traffic window for risky migrations.
6. Watch backend startup and Flyway logs.
7. Confirm app behavior after migration.

Validation command:

```bash
python scripts/check-flyway-migration-versions.py --changed-only
```

Never edit migrations already applied to production. Add a new forward-only migration instead.

## Phase 9: Backups and Restore

Backups are not complete until restore has been tested.

Back up:

- PostgreSQL database.
- Uploaded files.
- Any generated project artifacts that are not rebuildable.
- Caddy config if customized outside Git.
- Grafana dashboards if changed outside provisioning.

Minimum schedule:

- Daily PostgreSQL logical backup.
- Daily upload/artifact backup.
- Weekly restore test in a non-production environment.
- Provider snapshot before risky deployments.

Backup storage requirements:

- Stored off the production server.
- Encrypted at rest.
- Access-limited.
- Retained according to the business need.
- Monitored for failure.

Restore drill:

1. Provision a clean non-production environment.
2. Restore database backup.
3. Restore uploads/artifacts.
4. Start the app against restored data.
5. Confirm login and key workflows.
6. Record restore duration and any manual steps.

## Phase 10: Observability

Production observability should answer:

- Is the app up?
- Are users seeing errors?
- Are requests slow?
- Are jobs failing?
- Is the database healthy?
- Is disk space running out?
- Are admin actions auditable?
- Are backups succeeding?

Minimum dashboards:

- Backend availability and readiness.
- HTTP request rate, latency, and error rate.
- Caddy edge traffic, status codes, and upstream failures.
- Frontend/admin/landing browser-side errors and page-load telemetry.
- JVM memory, CPU, threads, and garbage collection.
- PostgreSQL connections and storage.
- Container CPU, memory, restarts, and disk usage.
- Host CPU, memory, disk, filesystem, and network pressure.
- AI provider request volume, failures, latency, and rate-limit events.
- Admin audit activity.

Minimum alerts:

- Backend readiness down.
- Caddy edge target down.
- High HTTP 5xx rate.
- Sustained high latency.
- PostgreSQL unavailable.
- Disk usage above 80 percent.
- Container restart loop.
- Frontend JavaScript error events.
- Backup failed or missing.
- TLS certificate renewal failure.
- Unexpected spike in admin failures or unauthorized requests.

Alert routing should go to a real operator channel. A dashboard nobody checks is not monitoring.

## Phase 11: Security Operations

Production security baseline:

- Use HTTPS only.
- Keep secrets out of Git.
- Use unique strong credentials for every environment.
- Use least privilege for database users and cloud credentials.
- Keep admin access limited and audited.
- Disable public access to internal tools.
- Patch the host and container images regularly.
- Scan images in CI.
- Review dependency advisories before releases.
- Rate-limit sensitive endpoints at the edge or application layer.
- Keep CORS restricted to real production origins.
- Validate file upload size and type limits.

Admin access should be treated as high-risk. Use a separate admin hostname, require explicit admin
roles, and review audit logs regularly.

## Phase 12: Release Process

Production release checklist:

1. Review code changes and migration changes.
2. Run backend compile/tests relevant to the change.
3. Build frontend/admin assets.
4. Validate Compose and Caddy config.
5. Run migration version checks.
6. Build and tag images.
7. Back up production data.
8. Deploy during an agreed window.
9. Watch logs, metrics, and alerts.
10. Run smoke tests.
11. Record deployed version and migration version.

Smoke tests:

- Open the user app.
- Log in.
- Open the admin app.
- Confirm backend readiness.
- Create or read a low-risk project.
- Confirm logs and metrics for the request.

## Phase 13: Rollback and Recovery

Prefer roll-forward fixes for ordinary application bugs. Rollback is necessary when the current
release causes severe user impact and a forward fix is slower than restoring service.

Rollback checklist:

1. Identify the deployed version and migration version.
2. Decide whether database changes are backward-compatible.
3. If backward-compatible, redeploy the previous image.
4. If not backward-compatible, restore from backup in a controlled recovery process.
5. Verify readiness and smoke tests.
6. Preserve logs for the incident review.

Do not run ad hoc database edits during an incident unless the change is reviewed, recorded, and
backed up.

## Phase 14: Maintenance Calendar

Daily:

- Check service health and alerts.
- Confirm backups completed.
- Review high-severity errors.

Weekly:

- Review admin audit logs.
- Review capacity trends.
- Restore-test a recent backup in non-production.
- Review dependency and image security advisories.

Monthly:

- Patch the server.
- Rotate high-risk credentials if needed.
- Review firewall and public DNS records.
- Review alert quality and noisy alerts.
- Review cost, disk growth, and retention settings.

Before major releases:

- Take a fresh backup.
- Confirm rollback plan.
- Confirm migration risk.
- Confirm operator availability.

## Phase 15: When to Move Beyond One Server

Move beyond a single Docker Compose server when any of these are true:

- Downtime during deploys is no longer acceptable.
- Database restore time exceeds the business tolerance.
- Traffic requires horizontal scaling.
- Compliance requires stronger isolation and audit controls.
- Operators need managed backups, failover, or private networking.
- The server becomes difficult to patch without service interruption.

Likely next steps:

- Managed PostgreSQL.
- Managed object storage for uploads and artifacts.
- CI-built images in a registry.
- Private network between services.
- Managed load balancer or production Caddy nodes.
- Centralized observability with durable retention.
- Staging environment that matches production.

## Launch Gate

Do not launch publicly until all items are true:

- Domain and HTTPS work.
- Only intended public routes are exposed.
- Admin login works for the correct account.
- Non-admin users cannot access admin APIs.
- `VARKA_ADMIN_BOOTSTRAP_ENABLED=false` and `VARKA_ADMIN_BOOTSTRAP_TOKEN` is empty after first
  admin setup.
- Database migrations have run successfully.
- Backups are configured and restore-tested.
- Metrics, logs, and alerts work.
- The server survives reboot and services restart.
- A rollback or recovery plan is written.
- Production secrets are not committed or printed in logs.
