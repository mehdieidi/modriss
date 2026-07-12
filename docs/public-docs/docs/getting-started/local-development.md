# Local Development

## Toolchain

- Java 17 or newer
- Maven 3.9 or newer
- PostgreSQL 16 with pgvector
- Python 3
- Node.js and npm
- Docker, recommended for PostgreSQL and LocalStack

## Run PostgreSQL and Backend

```powershell
docker compose up -d postgres
mvn -pl apps/backend -am spring-boot:run
```

The backend defaults to:

```text
VARKA_DB_URL=jdbc:postgresql://localhost:5432/varka
VARKA_DB_USER=varka
VARKA_DB_PASSWORD=varka
```

Flyway applies all migrations at startup.

## Run the Frontend

The frontend is static and expects the backend at `http://127.0.0.1:8080`, configured in
`apps/frontend/backend-config.js`.

```powershell
python -m http.server 8082 --directory apps/frontend
```

Then open `http://127.0.0.1:8082`.

The modeling frontend uses the AntV G6 canvas renderer configured through `diagramEditor.renderer`
in `GET /api/modeling/config`.

## Build and Test

```powershell
mvn test
mvn -pl apps/backend -am package
```

Build an individual module with its dependencies:

```powershell
mvn -pl packages/java/platform-modeling -am test
```

## Formatting

```powershell
python scripts/format.py
python scripts/format.py --check
```

The formatter applies Google Java Format through Spotless and Prettier to supported web,
documentation, and configuration files. It also performs conservative whitespace normalization for
MDE sources.

## Useful Development Checks

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8080/api/modeling/config
```

Use `127.0.0.1` when a local proxy intercepts `localhost`.
