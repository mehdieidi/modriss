# Deployment

This page is the public production checklist. Use the published
[DevOps/SRE overview](devops-sre.md) for the broader operational handbook and ownership context.

## Local Compose Deployment

```bash
docker compose up --build
```

The root `compose.yaml` includes `deploy/compose.yaml`. Compose starts Caddy, PostgreSQL, backend,
modeling frontend, admin app, landing site, the selected AWS emulator (Floci by default), Dozzle, Prometheus, Grafana, Loki, Promtail,
postgres-exporter, node-exporter, and cAdvisor.

The preferred local entrypoint is Caddy:

| Surface      | URL                                |
| ------------ | ---------------------------------- |
| Landing      | `http://localhost:8088`            |
| Editor       | `http://editor.localhost:8088`     |
| Backend API  | `http://api.localhost:8088`        |
| Admin app    | `http://admin.localhost:8088`      |
| Grafana      | `http://grafana.localhost:8088`    |
| Logs         | `http://logs.localhost:8088`       |
| Prometheus   | `http://prometheus.localhost:8088` |
| Loki         | `http://loki.localhost:8088`       |
| AWS emulator | `http://floci.localhost:8088`      |

Direct service ports remain available for debugging.

## Production Target

For a first production deployment, use one Linux server with Docker Compose and Caddy as the edge.
Only Caddy should be public. PostgreSQL, Prometheus, Loki, the AWS emulator, Dozzle, exporters, and direct
backend ports should stay private.

Recommended public hostnames:

| Surface | Example hostname              | Exposure                                  |
| ------- | ----------------------------- | ----------------------------------------- |
| Editor  | `https://editor.example.com`  | Public                                    |
| API     | `https://api.example.com`     | Public, authenticated and rate-limited    |
| Admin   | `https://admin.example.com`   | Restricted by admin auth and network rule |
| Landing | `https://example.com`         | Public                                    |
| Grafana | `https://grafana.example.com` | VPN, SSO, or IP allowlist only            |

## Domain and DNS

1. Register the domain with a registrar.
2. Point the domain to the provider nameservers you plan to use.
3. Create `A` records for each hostname that should reach the server.
4. Use a low TTL, such as 300 seconds, during launch.
5. Confirm records from outside the server.

```bash
dig example.com
dig editor.example.com
dig api.example.com
dig admin.example.com
```

## Server Setup

Use a clean Ubuntu LTS server as the baseline.

Minimum setup:

- Create a non-root deploy user with sudo access.
- Disable password SSH login and use SSH keys.
- Install Docker Engine and the Docker Compose plugin.
- Enable automatic security updates.
- Open only SSH, HTTP, and HTTPS in the firewall.
- Add provider snapshots or another server-level backup.
- Configure Docker and system log rotation.

Example firewall intent:

```bash
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

## Production Configuration

Create `.env` on the server and never commit it.

Required changes from local defaults:

- Use strong random `POSTGRES_PASSWORD`.
- Use strong `GRAFANA_ADMIN_PASSWORD`.
- Set `MODRISS_ALLOWED_ORIGINS` to the exact HTTPS app, admin, and API origins.
- Configure AI provider keys through environment variables or a secret manager.
- Temporarily enable admin bootstrap only for first admin setup.
- Disable admin bootstrap immediately after the first admin role is assigned.
- Remove the AWS emulator and direct observability exposure unless there is a controlled operational need.
- Keep upload, PostgreSQL, Grafana, Prometheus, and Loki data on persistent volumes.

Example:

```env
MODRISS_ALLOWED_ORIGINS=https://example.com,https://editor.example.com,https://admin.example.com,https://api.example.com
MODRISS_ADMIN_BOOTSTRAP_ENABLED=true
MODRISS_ADMIN_BOOTSTRAP_EMAILS=owner@example.com
MODRISS_ADMIN_BOOTSTRAP_TOKEN=replace-with-long-random-token
```

## Caddy Production Routing

The local Caddyfile uses `localhost` and disables automatic HTTPS. Production should use real
hostnames and Caddy-managed TLS.

Example shape:

```caddyfile
example.com {
  reverse_proxy landing:8083
}

editor.example.com {
  reverse_proxy frontend:8082
}

api.example.com {
  reverse_proxy backend:8080
}

admin.example.com {
  reverse_proxy admin:8084
}
```

Protect admin and observability routes with VPN, SSO, IP allowlisting, or another access-control
layer before exposing them on the internet.

## Deploy

First deployment checklist:

1. Confirm DNS points to the server.
2. Confirm ports `80` and `443` are open.
3. Copy or create the production `.env`.
4. Configure production Caddy hostnames.
5. Validate Compose.
6. Start the stack.
7. Confirm Caddy obtains TLS certificates.
8. Confirm backend readiness.
9. Log in and complete admin bootstrap.
10. Disable admin bootstrap and remove the bootstrap token.
11. Confirm metrics, logs, and audit events work.
12. Create and restore-test the first backup.

Useful commands:

```bash
docker compose config -q
docker compose up -d
docker compose ps
docker compose logs -f caddy backend
curl -I https://example.com
curl -I https://editor.example.com
curl https://api.example.com/actuator/health/readiness
```

## Health Checks

- Backend application: `/api/health`
- Backend actuator: `/actuator/health`
- Backend readiness: `/actuator/health/readiness`
- Backend metrics: `/actuator/prometheus`
- Floci, if deployed: `/_floci/health`; LocalStack: `/_localstack/health`

PostgreSQL uses `pg_isready`. Prometheus scrapes backend, Caddy, PostgreSQL exporter, host,
container, and observability targets.

## Database Operations

Flyway runs automatically at backend startup. Add migrations only with the helper script and validate
changed migration versions before release.

```bash
python scripts/flyway-next-migration.py "short description" --location platform
python scripts/check-flyway-migration-versions.py --changed-only
```

Back up production PostgreSQL before deploying migrations. Never edit migrations already applied to
production; add a forward-only migration instead.

## Release and Rollback

Before each production release:

- Run focused backend tests and compile checks for changed modules.
- Build the admin and landing apps when they changed.
- Validate Compose and Caddy configuration.
- Run Flyway migration checks.
- Back up PostgreSQL and uploaded/generated files.
- Deploy during an agreed window.
- Watch Caddy logs, backend logs, readiness, HTTP 5xx rate, latency, job failures, database health,
  and host disk pressure.

Prefer roll-forward fixes. If rollback is required, redeploy the previous image only when database
changes are backward-compatible. Otherwise restore from backup in a controlled recovery process.
