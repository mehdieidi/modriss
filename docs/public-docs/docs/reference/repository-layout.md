# Repository Layout

```text
apps/
  backend/                 Spring Boot API and assistant
  frontend/                Browser modeling application
  landing/                 Public landing page
config/                    Tooling and static-analysis configuration
deploy/                    Docker Compose stack, Dockerfile, deployment scripts
docs/
  adr/                     Architecture Decision Records
  api/                     REST and WebSocket API references
  diagrams/                Mermaid architecture diagrams
  internal/                Contributor guides and deep-dive references
  public-docs/             Published MkDocs site
infra/
  grafana/                 Dashboard definitions
  prometheus/              Alert rule definitions
mde/
  metamodels/              Emfatic and combined Ecore
  validation/              EVL validation profiles
  transformations/         ETL/EOL model transformations
  generation/              EGX/EGL artifact generation
  samples/                 XMI samples and case study
packages/java/
  platform-kernel/         Shared kernel types (PlatformException, ModelLevel)
  platform-storage-api/    PlatformStore persistence port
  platform-identity/         Auth and user lifecycle
  platform-project/        Projects and membership
  platform-modeling/       Metamodel resolution, JSON/XMI bridge, config, layout
  platform-model/          Model workspace CRUD, validation, import/export
  platform-artifact/       Generated artifact storage
  platform-transformation/ MDE pipeline and async job orchestration
  platform-storage-postgres/ PostgreSQL adapter and Flyway migrations
  mde-evl-validator/       Reusable EVL runner
  mde-etl-runner/          Reusable ETL runner
  mde-m2t-runner/          Reusable EGX/EGL runner
tools/
  mde-cli/                 Emfatic-to-Ecore compiler CLI
  mde-evl-cli/             Validation CLI
  mde-etl-cli/             Transformation CLI
  mde-m2t-cli/             Generation CLI
scripts/                   Repository automation (format, lint, verify, Flyway check)
tests/                     Cross-cutting test placeholders
```

## Maven Dependency Direction

Feature libraries depend inward on the shared kernel and storage port. The backend composes
feature modules plus the Postgres storage adapter. MDE runner modules stay Spring-free and are
reused from CLIs and transformation services.

```text
platform-kernel
platform-storage-api → platform-kernel

platform-identity → platform-storage-api
platform-project → platform-identity
platform-modeling → platform-kernel
platform-model → platform-project, platform-modeling, mde-evl-validator
platform-artifact → platform-project
platform-transformation → platform-model, platform-artifact, mde-etl-runner, mde-m2t-runner

platform-storage-postgres → platform-storage-api + all feature domain types

apps/backend → platform-storage-postgres + feature modules
```

This separation keeps formal MDE execution reusable outside the web backend and isolates
PostgreSQL behind `PlatformStore`.

## Canonical Documentation

The repository also contains a detailed Mermaid diagram package in `docs/diagrams/`, a checked-in
OpenAPI contract in `docs/api/openapi/openapi.yaml`, storage and assistant guides under
`docs/internal/`, generated-artifact operations guides, and module-specific READMEs.
