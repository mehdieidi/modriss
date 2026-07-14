# Deployment

## Development Compose Deployment

```bash
docker compose up --build
```

Compose starts PostgreSQL, backend, frontend, LocalStack, landing site, and Dozzle. The modeling
frontend uses the AntV G6 canvas renderer. The backend container is built with Maven, runs on a Java
17 JRE, copies the `mde/` tree into the image, and runs as a non-root user.

## Health Checks

- Backend application: `/api/health`
- Backend actuator: `/actuator/health`
- Backend readiness: `/actuator/health/readiness`
- LocalStack: `/_localstack/health`

PostgreSQL uses `pg_isready`.

## Production Considerations

The included Compose configuration is a development-oriented baseline, not a complete public
production deployment. A production design should add:

- TLS and a reverse proxy or ingress
- Managed secret storage and rotated credentials
- Restricted database and actuator access
- Backups, restoration tests, and migration procedures
- Persistent volumes and retention policies
- Centralized logs, metrics, alerts, and traces
- Explicit CORS origin policy for REST and SSE
- Provider rate limits and cost controls for AI
- Resource limits and horizontal/vertical scaling decisions
- Security review of generated-artifact download and editing workflows

## Profiles and Logs

The backend supports development and production logging profiles. Production logging can emit
structured JSON with a service field. Request logging includes a request ID for correlation.

## Database Operations

Flyway runs automatically at backend startup. Test every migration against both an empty database
and a representative existing database. Back up long-lived data before deployment changes.
