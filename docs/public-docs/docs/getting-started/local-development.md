# Local Development Workflow

This workflow describes a normal MODRISS development day from first checkout through implementation,
verification, and handoff. It assumes PowerShell on Windows, but the same commands work with small
shell syntax changes on Linux or macOS.

## Toolchain

- Java 17 or newer
- Maven 3.9 or newer
- Docker and Docker Compose
- PostgreSQL 16 with pgvector, usually through Compose
- Python 3 for the static modeling frontend server and helper scripts
- Node.js and npm for the admin app, landing app, docs, and shared web tooling

## First Setup

Clone the repository, copy the environment template, and install Node dependencies for the web apps
you intend to edit.

```powershell
cp .env.example .env
npm install
Push-Location apps/admin; npm install; Pop-Location
Push-Location apps/landing; npm install; Pop-Location
```

Keep secrets in `.env` or in your shell environment. Do not commit `.env`.

## Start of Day

Start by syncing your branch, checking what is already modified, and deciding whether you need the
full Compose stack or a faster split process.

```powershell
git status --short
docker compose ps
```

Use the full stack when you are checking integration between apps, Caddy routing, observability,
the selected AWS emulator (Floci by default), or production-like behavior:

```powershell
docker compose up --build
```

Useful local entrypoints:

| Surface                      | Direct URL              | Caddy URL                          |
| ---------------------------- | ----------------------- | ---------------------------------- |
| Landing                      | `http://127.0.0.1:8083` | `http://localhost:8088`            |
| Editor                       | `http://127.0.0.1:8082` | `http://editor.localhost:8088`     |
| Backend API                  | `http://127.0.0.1:8080` | `http://api.localhost:8088`        |
| Admin app                    | `http://127.0.0.1:8084` | `http://admin.localhost:8088`      |
| Grafana                      | `http://127.0.0.1:3000` | `http://grafana.localhost:8088`    |
| Prometheus                   | `http://127.0.0.1:9090` | `http://prometheus.localhost:8088` |
| Loki                         | `http://127.0.0.1:3100` | `http://loki.localhost:8088`       |
| Dozzle                       | `http://127.0.0.1:9999` | `http://logs.localhost:8088`       |
| AWS emulator (Floci default) | `http://127.0.0.1:4566` | `http://floci.localhost:8088`      |

Use split-process development when you are iterating on backend code and static frontend files:

```powershell
docker compose up -d postgres
mvn -pl apps/backend -am spring-boot:run
python -m http.server 8082 --directory apps/frontend
```

The static modeling frontend reads its backend URL from `apps/frontend/backend-config.js`. The
Compose `frontend` service writes a container-specific version automatically.

## Development Loop

Work in the smallest running slice that exercises the change.

For backend and platform library changes:

1. Edit the relevant module under `apps/backend` or `packages/java`.
2. Run the backend locally or rebuild the backend container.
3. Exercise the changed REST route or UI workflow.
4. Run focused Maven tests for the changed module.

Examples:

```powershell
mvn -pl apps/backend -am test
mvn -pl packages/java/platform-modeling -am test
mvn -pl packages/java/platform-assistant -am test
```

For modeling, metamodel, EVL, ETL, or generation changes:

1. Edit files under `mde/` or the MDE runner packages.
2. Run the matching focused CLI, backend test, or sample workflow.
3. Rebuild the backend container before testing through Compose because `mde/` is copied into the
   backend image.

```powershell
docker compose up --build --force-recreate backend
```

For the static modeling frontend:

1. Serve `apps/frontend` with Python or use the Compose `frontend` service.
2. Keep the backend running.
3. Validate the changed flow in the browser.
4. Check browser console errors and network requests.

For the admin app:

```powershell
Push-Location apps/admin
npm run build
Pop-Location
docker compose up --build --force-recreate admin
```

For the landing app:

```powershell
Push-Location apps/landing
npm run build
Pop-Location
docker compose up --build --force-recreate landing
```

## Database and Migrations

Flyway runs automatically when the backend starts. Use the helper script when adding migrations; do
not choose migration versions by hand.

```powershell
python scripts/flyway-next-migration.py "short description" --location platform
python scripts/check-flyway-migration-versions.py --changed-only
```

Use `--location assistant` for assistant-storage changes. Migration versions are global across
platform and assistant migration folders.

## Observability While Developing

Use Dozzle for quick logs and Grafana/Loki when you need request correlation.

- Backend health: `http://127.0.0.1:8080/actuator/health/readiness`
- API health: `http://127.0.0.1:8080/api/health`
- Metrics: `http://127.0.0.1:8080/actuator/prometheus`
- Modeling config: `http://127.0.0.1:8080/api/modeling/config`

Every backend response includes `X-Request-Id`; search that value in Loki or Dozzle when debugging a
specific request.

## Before Ending the Work

Run verification that matches the risk of the change.

```powershell
mvn -pl apps/backend -am -DskipTests compile
mvn -pl packages/java/platform-modeling -am test
docker compose config -q
```

Use broader verification before a PR or release:

```powershell
mvn test
python scripts/verify.py
```

`python scripts/format.py` and formatting checks are available, but run them only when you intend to
apply or verify formatting changes.

## Shutdown

Stop the full stack when you no longer need it:

```powershell
docker compose down
```

Keep volumes when you want local data to survive. Use volume deletion only when you intentionally
want a clean database, uploads, Prometheus, Loki, Grafana, and selected emulator state.
