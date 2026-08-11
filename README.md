# Varka

**Model business intent → refine architecture → generate deployable AWS serverless projects.**

Varka is an AI-assisted, model-driven platform. You work in three formal modeling levels
(CIM, PIM, AWS PSM) in the browser, validate with Eclipse Epsilon, transform between levels, and
generate a reviewable project (infrastructure, Go handlers, contracts, tests, docs). An optional
assistant automatically chooses a bounded conceptual generator, an inspect/contract editing loop,
or a read-only answer, and commits changes only after structural Ecore/EMF validation.

```text
CIM  ──validate/transform──▶  PIM  ──validate/transform──▶  AWS PSM  ──generate──▶  deployable project
         EVL / ETL                  EVL / ETL                    EVL / EGX·EGL
```

Thesis context and long-form rationale:
[docs/internal/project-description.md](docs/internal/project-description.md)

## Run it

**Needs:** Docker Compose, a modern browser.

```bash
docker compose up --build
```

| What                                 | URL                                     |
| ------------------------------------ | --------------------------------------- |
| Modeling app                         | <http://127.0.0.1:8082>                 |
| API health                           | <http://127.0.0.1:8080/api/health>      |
| API explorer                         | <http://127.0.0.1:8080/swagger-ui.html> |
| Landing page                         | <http://127.0.0.1:8083>                 |
| Container logs (Dozzle)              | <http://127.0.0.1:9999>                 |
| LocalStack (generated project tests) | <http://127.0.0.1:4566>                 |

**First project:** register → create a project → open CIM → import
[`mde/samples/cim.xmi`](mde/samples/cim.xmi) or model from scratch → **Generate PIM** → **Generate
PSM** → **Generate Artifacts** → download the ZIP from the artifact explorer.

[Quickstart guide](docs/public-docs/docs/getting-started/quickstart.md) ·
[Enable the AI assistant](docs/internal/ai/assistant.md)

## Develop it

| Tool       | Version                                            |
| ---------- | -------------------------------------------------- |
| Java       | 17+                                                |
| Maven      | 3.9+                                               |
| PostgreSQL | 16 + pgvector (or `docker compose up -d postgres`) |
| Python 3   | static frontend server                             |
| Node.js    | format/lint tooling                                |

```bash
docker compose up -d postgres
mvn -pl apps/backend -am spring-boot:run          # API on :8080
python -m http.server 8082 --directory apps/frontend # UI on :8082
```

Backend URL for the frontend: [`apps/frontend/backend-config.js`](apps/frontend/backend-config.js).

```bash
python scripts/verify.py    # format + lint + tests
mvn test
```

[Local development](docs/public-docs/docs/getting-started/local-development.md) ·
[Configuration](docs/public-docs/docs/reference/configuration.md)

## What's in the repo

```text
apps/           backend (Spring Boot), modeling frontend, landing page
mde/            metamodels, EVL, ETL, generation templates, samples
packages/java/  platform libraries, MDE runners, assistant, Postgres adapter
tools/          standalone MDE CLIs (validate, transform, generate, compile Emfatic)
docs/           public MkDocs site, diagrams, API reference, internal guides
deploy/         Docker Compose stack and Dockerfile
```

[Full layout and Maven dependency rules](docs/public-docs/docs/reference/repository-layout.md)

## Documentation

| Start here             |                                                                                                                                           |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| User guides & concepts | [docs/public-docs/](docs/public-docs/) (MkDocs)                                                                                           |
| REST & realtime API    | [rest-api](docs/api/rest-api.md) · [realtime](docs/public-docs/docs/reference/realtime-api.md) · [OpenAPI](docs/api/openapi/openapi.yaml) |
| Architecture diagrams  | [docs/diagrams/](docs/diagrams/)                                                                                                          |
| Assistant setup        | [docs/internal/ai/assistant.md](docs/internal/ai/assistant.md)                                                                            |
| Generated AWS projects | [docs/internal/artifacts/deployment-and-testing.md](docs/internal/artifacts/deployment-and-testing.md)                                    |
| Status & roadmap       | [status-roadmap](docs/public-docs/docs/contributing/status-roadmap.md)                                                                    |

## Contributing

Copy [`.env.example`](.env.example) to `.env`, run `python scripts/verify.py` before opening a PR.
See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

[MIT](LICENSE) — Copyright (c) 2026 Mehdi Eidi
