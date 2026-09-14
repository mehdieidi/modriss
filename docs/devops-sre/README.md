# DevOps and SRE Handbook

This folder documents how MODRISS is operated as a running platform: deployment, reliability,
observability, incident response, backups, access control, and production-readiness practices.

## DevOps or SRE?

Both terms apply, but they emphasize different concerns.

- **DevOps** is the engineering practice of making development, release, infrastructure, and
  operations work as one system. In MODRISS this includes Docker Compose, container builds,
  environment configuration, database migrations, deployment scripts, and CI/CD expectations.
- **SRE** is the reliability practice of running the platform against explicit service objectives.
  In MODRISS this includes monitoring, alerting, incident response, error budgets, operational
  runbooks, capacity planning, and post-incident learning.

For this project, use **DevOps/SRE** as the umbrella term. The admin panel is the operational
control plane; Prometheus, Grafana, Loki, Promtail, Actuator, and structured logs are the
observability stack.

## Local Stack

The canonical local stack is `deploy/compose.yaml`, included by the root `compose.yaml`.

```powershell
docker compose up --build
```

Primary endpoints:

| Surface     | URL                     | Purpose                             |
| ----------- | ----------------------- | ----------------------------------- |
| Backend API | `http://127.0.0.1:8080` | Spring Boot API and Actuator        |
| Caddy edge  | `http://127.0.0.1:8088` | Local reverse proxy entrypoint      |
| User app    | `http://127.0.0.1:8082` | Modeling UI                         |
| Admin app   | `http://127.0.0.1:8084` | Admin control plane                 |
| Prometheus  | `http://127.0.0.1:9090` | Metrics scrape and alert rules      |
| Grafana     | `http://127.0.0.1:3000` | Dashboards and log exploration      |
| Loki        | `http://127.0.0.1:3100` | Log store                           |
| Dozzle      | `http://127.0.0.1:9999` | Container log viewer                |
| LocalStack  | `http://127.0.0.1:4566` | AWS emulator for generated projects |

## Documents

- [Admin control plane](admin-control-plane.md)
- [Caddy edge proxy](edge-caddy.md)
- [Observability stack](observability.md)
- [Deployment and release operations](deployment-and-release.md)
- [Production server deployment and operations runbook](production-server-runbook.md)
- [Reliability model](reliability.md)
- [Incident response](incident-response.md)
- [Backup and recovery](backup-and-recovery.md)
- [Security operations](security-operations.md)

## Operational Principles

1. Prefer explicit admin actions over direct database edits.
2. Every sensitive administrative action must be authenticated, authorized, and audited.
3. Use request IDs to connect UI errors, backend logs, audit events, and operator reports.
4. Treat migrations as forward-only changes after they are shared.
5. Keep local defaults convenient, but make production secrets, credentials, and admin access
   explicit.
6. Verify deployability with build/type checks and targeted tests before releasing.
7. Monitor symptoms first: user-facing errors, latency, failed jobs, assistant failures, and
   readiness failures.
