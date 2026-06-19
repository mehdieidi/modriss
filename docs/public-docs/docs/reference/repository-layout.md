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
  platform-domain/         Shared domain records and enums
  platform-application/    Application services and persistence port
  platform-storage-postgres/ PostgreSQL adapter and Flyway migrations
  platform-modeling/       Metamodel resolution, JSON/XMI bridge, config, layout
  mde-evl-validator/       Reusable EVL runner
  mde-etl-runner/          Reusable ETL runner
  mde-m2t-runner/          Reusable EGX/EGL runner
scripts/                   Repository automation (format, lint, verify, Flyway check)
tests/                     Cross-cutting test placeholders
tools/
  mde-cli/                 Emfatic-to-Ecore compiler CLI
  mde-evl-cli/             Validation CLI
  mde-etl-cli/             Transformation CLI
  mde-m2t-cli/             Generation CLI
```

## Maven Dependency Direction

The backend composes domain, application, storage, and modeling modules. The application module
uses the modeling and reusable MDE runner modules. Storage implements the application persistence
port. CLI modules wrap the corresponding reusable runners.

This separation keeps formal execution reusable outside the web backend and isolates PostgreSQL
behind `PlatformStore`.

## Canonical Documentation

The repository also contains a detailed Mermaid diagram package in `docs/diagrams/`, a checked-in
OpenAPI contract in `docs/api/openapi/openapi.yaml`, storage and assistant guides under
`docs/internal/`, generated-artifact operations guides, and module-specific READMEs.
