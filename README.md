# Modless

AI-assisted, model-driven low-code platform for designing, transforming, and generating AWS
serverless applications.

Modless combines formal CIM/PIM/PSM modeling, Eclipse Epsilon validation and transformations, and an
in-browser modeling editor with optional Spring AI assistance. From a platform-specific model, the
toolchain produces reviewable, deployable serverless project artifacts.

For the full thesis-oriented project narrative, architecture rationale, and planned features, see
[docs/project-description.md](docs/project-description.md).

## Features

- **Three-level modeling** — CIM, PIM, and PSM workspaces with metamodel-driven palettes and validation
- **Semi-automated pipeline** — CIM → PIM → PSM transformations and PSM → AWS artifact generation
- **Constraint validation** — EVL rules enforced across modeling levels
- **Artifact explorer** — Browse, edit, and export generated projects as ZIP archives
- **AI modeling assistant** — Natural-language model edits with metamodel-aware guardrails (optional)
- **Production-oriented backend** — Spring Boot API, PostgreSQL persistence, Flyway migrations

## Tech Stack

| Layer    | Technologies                                |
| -------- | ------------------------------------------- |
| Backend  | Java 17, Maven, Spring Boot, Spring AI      |
| Frontend | HTML, CSS, vanilla JavaScript (ES modules)  |
| MDE      | Emfatic/Ecore, Epsilon (EVL, ETL, EGL, EGX) |
| Data     | PostgreSQL 16, pgvector, Flyway             |
| Tooling  | Python 3, Node.js, Docker Compose           |

## Quick Start

**Prerequisites:** Docker with Compose support and a modern browser.

From the repository root:

```bash
docker compose up --build
```

When PostgreSQL and the backend are healthy, open:

| Service              | URL                                   |
| -------------------- | ------------------------------------- |
| Modeling frontend    | http://127.0.0.1:8082                 |
| Backend health       | http://127.0.0.1:8080/api/health      |
| OpenAPI / Swagger UI | http://127.0.0.1:8080/swagger-ui.html |
| Landing page         | http://127.0.0.1:8083                 |

A minimal first run: register a user, create a project, open the CIM workspace, import
`mde/samples/cim.xmi` or model from scratch, then run **Generate PIM**, **Generate PSM**, and
**Generate Artifacts**.

Step-by-step instructions: [docs/public-docs/docs/getting-started/quickstart.md](docs/public-docs/docs/getting-started/quickstart.md)

## Local Development

For day-to-day backend and frontend work without the full Compose stack:

| Tool          | Version                                                 |
| ------------- | ------------------------------------------------------- |
| Java          | 17+                                                     |
| Maven         | 3.9+                                                    |
| Python        | 3                                                       |
| Node.js / npm | Current LTS recommended                                 |
| PostgreSQL    | 16 with pgvector (or use Compose for the database only) |

```bash
# Database (optional if you already have PostgreSQL)
docker compose up -d postgres

# Backend
mvn -pl apps/backend -am spring-boot:run

# Frontend static server (separate terminal)
python -m http.server 8082 --directory apps/frontend
```

The frontend reads the backend URL from `apps/frontend/backend-config.js` (default
`http://127.0.0.1:8080`).

Build and test:

```bash
mvn test
mvn -pl apps/backend -am package
```

Detailed setup, health checks, and AI configuration:
[docs/public-docs/docs/getting-started/local-development.md](docs/public-docs/docs/getting-started/local-development.md)

## Repository Layout

```text
apps/
  backend/                 Spring Boot API and assistant
  frontend/                Browser modeling application
  landing/                 Public landing page
docs/                      Engineering documentation and diagrams
mde/                       Metamodels, validation, transformations, generation, samples
packages/java/             Domain, application, storage, modeling, and MDE runner modules
tools/                     Standalone MDE CLI utilities
scripts/                   Repository automation (format, lint)
```

Module boundaries and dependency direction:
[docs/public-docs/docs/reference/repository-layout.md](docs/public-docs/docs/reference/repository-layout.md)

## Documentation

| Topic                                | Location                                                                                               |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------ |
| Public docs site (MkDocs)            | [docs/public-docs/](docs/public-docs/)                                                                 |
| Architecture diagrams                | [docs/diagrams/](docs/diagrams/)                                                                       |
| REST API                             | [docs/api/rest-api.md](docs/api/rest-api.md) · [OpenAPI](docs/api/openapi/openapi.yaml)                |
| PostgreSQL storage                   | [docs/postgres-storage.md](docs/postgres-storage.md)                                                   |
| AI assistant                         | [docs/ai-assistant.md](docs/ai-assistant.md)                                                           |
| Generated artifacts                  | [docs/generated-artifact-deployment-and-testing.md](docs/generated-artifact-deployment-and-testing.md) |
| Project description (thesis context) | [docs/project-description.md](docs/project-description.md)                                             |

## Code Quality

### Formatting

Pinned formatters keep style consistent across machines:

- **Java** — Spotless with Google Java Format
- **Web, docs, config** — Prettier
- **MDE sources** — Conservative whitespace normalization (`.eol`, `.etl`, `.evl`, `.ecore`, …)

```bash
python scripts/format.py          # apply formatting
python scripts/format.py --check  # verify only
```

Prettier is installed automatically from `package-lock.json` when `node_modules` is missing.

### Linting

Scoped static analysis runs per technology stack:

```bash
python scripts/lint.py                    # all scopes
python scripts/lint.py --scope java       # single scope
python scripts/lint.py --scope web
python scripts/lint.py --list-scopes      # show available scopes
```

| Scope      | Tools                             |
| ---------- | --------------------------------- |
| `java`     | Checkstyle, PMD, SpotBugs (Maven) |
| `web`      | ESLint, Stylelint                 |
| `python`   | Ruff                              |
| `yaml`     | yamllint                          |
| `markdown` | markdownlint-cli2                 |
| `docker`   | Hadolint (requires Docker)        |
| `all`      | Every scope above                 |

Python lint dependencies install automatically from `requirements-lint.txt` when needed. Node lint
dependencies install automatically from `package-lock.json` when needed.

Equivalent npm entry point: `npm run lint`

## CLI Tools

Standalone CLIs wrap the reusable MDE runners for validation, transformation, generation, and
metamodel compilation. Reference:
[docs/public-docs/docs/reference/cli-tools.md](docs/public-docs/docs/reference/cli-tools.md)

## License

[MIT](LICENSE) — Copyright (c) 2026 Mehdi Eidi
