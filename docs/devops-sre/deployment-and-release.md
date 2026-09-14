# Deployment and Release Operations

This guide covers the operational path for changing and running MODRISS. It does not replace
environment-specific production infrastructure design; it defines the baseline expectations.

For an end-to-end real-server launch plan, use
[Production server deployment and operations runbook](production-server-runbook.md).

## Local Deployment

Run the full stack from the repository root:

```powershell
docker compose up --build
```

The root `compose.yaml` includes `deploy/compose.yaml`, which is the canonical stack definition.
The preferred browser entrypoint is the Caddy edge proxy at `http://localhost:8088`; direct service
ports remain available for debugging.

Local Caddy routes:

| Route                              | Target service |
| ---------------------------------- | -------------- |
| `http://localhost:8088`            | `landing`      |
| `http://editor.localhost:8088`     | `frontend`     |
| `http://api.localhost:8088`        | `backend`      |
| `http://admin.localhost:8088`      | `admin`        |
| `http://grafana.localhost:8088`    | `grafana`      |
| `http://prometheus.localhost:8088` | `prometheus`   |
| `http://logs.localhost:8088`       | `dozzle`       |
| `http://loki.localhost:8088`       | `loki`         |
| `http://localstack.localhost:8088` | `localstack`   |

The current Compose stack includes application workloads (`backend`, `frontend`, `admin`,
`landing`), stateful/runtime support (`postgres`, `localstack`), edge/log access (`caddy`,
`dozzle`), and the observability stack (`prometheus`, `grafana`, `loki`, `promtail`,
`postgres-exporter`, `node-exporter`, `cadvisor`).

## Configuration

Use `.env` for local overrides. Use a secret manager or orchestrator-managed secrets in production.

Important values:

| Variable                         | Purpose                                                                                        |
| -------------------------------- | ---------------------------------------------------------------------------------------------- |
| `POSTGRES_*`                     | PostgreSQL database, user, password, and published port.                                       |
| `CADDY_HTTP_PORT`                | Published local port for the Caddy edge proxy.                                                 |
| `MODRISS_DB_*`                   | Backend JDBC connection settings.                                                              |
| `MODRISS_ALLOWED_ORIGINS`        | CORS origins for browser apps.                                                                 |
| `MODRISS_ADMIN_BOOTSTRAP_EMAILS` | Initial admin role bootstrap emails.                                                           |
| `MODRISS_METRICS_ENABLED`        | Prometheus metrics export.                                                                     |
| `GRAFANA_ADMIN_PASSWORD`         | Grafana local admin password.                                                                  |
| `MODRISS_AI_*`                   | Unified mode, Arvan/Gemma JSON protocol, budgets, timeouts, rate limits, and circuit behavior. |

## Release Checklist

Before a release:

- Build the backend with `mvn -pl apps/backend -am -DskipTests compile`.
- Run focused tests for changed backend modules.
- Build the admin app with `npm run build` from `apps/admin`.
- Verify `npm audit` in `apps/admin`.
- Validate Compose with `docker compose config -q`.
- Confirm Flyway migrations are forward-only and named correctly with
  `python scripts/check-flyway-migration-versions.py --changed-only`.
- Back up persistent PostgreSQL data before applying migrations to long-lived environments.
- Review `.env` for accidental local defaults, test credentials, or public exposure.

## Database Migrations

Flyway owns PostgreSQL schema changes. Migration files live in platform modules and are packaged into
the backend image.

Rules:

- Do not edit already-applied migrations in shared environments.
- Add new forward-only migrations with `python scripts/flyway-next-migration.py "<description>"`.
- Treat migration versions as global across platform and assistant migration folders.
- Make destructive migrations explicit and backed by a backup/restore plan.
- Keep admin tables and audit tables append-friendly.

## Rollback Strategy

Prefer roll-forward fixes for application code. Database rollback should be treated as a recovery
operation, not the default deployment path.

Minimum rollback plan:

1. Keep the previous backend/admin image available.
2. Keep a PostgreSQL backup from before the migration.
3. Record which Flyway version was deployed.
4. If data migration is involved, test restore in a non-production environment first.

## Production Hardening

The Compose stack is useful for local and small controlled deployments. A production design should
also include:

- TLS termination.
- Private networking for PostgreSQL, Prometheus, Loki, and Actuator.
- External secret management.
- Durable backup storage.
- Log and metric retention policies.
- Resource limits and health checks in the orchestrator.
- CI/CD gates for tests, image scanning, migrations, and deployment approval.

For an internet-facing single-server deployment, publish only Caddy ports `80` and `443`. Remove
direct public port mappings for the backend, admin app, frontend, PostgreSQL, Grafana, Prometheus,
Loki, Dozzle, LocalStack, and exporters unless there is a documented restricted-access reason.
