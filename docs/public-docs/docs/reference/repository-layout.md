# Repository Layout

```text
apps/
  backend/                 Spring Boot API and assistant
  frontend/                Browser modeling application
  landing/                 Public landing page
config/                    Tooling and static-analysis configuration
deploy/                    Docker Compose stack, Dockerfile, deployment scripts
docs/
  api/                     REST and SSE API references
  diagrams/                Mermaid architecture diagrams
  internal/                Contributor guides and deep-dive references
  public-docs/             Published MkDocs site
infra/
  grafana/                 Dashboard definitions
  prometheus/              Alert rule definitions
methodology/
  process/                 Process definitions, method library, and coverage matrices
  modeling-framework/
    metamodels/            Emfatic and combined Ecore
    notation/              Concrete visual syntax definitions
    validation/            EVL validation profiles
    transformations/       ETL/EOL model transformations
    generation/            EGX/EGL artifact generation
    samples/               XMI samples and case study
packages/
  java/
    platform-shared-types/             Shared types (PlatformException, ModelLevel)
    platform-storage-api/              PlatformStore persistence port
    platform-user-authentication/      Authentication and user lifecycle
    platform-project-management/       Projects and membership
    platform-modeling-infrastructure/  Metamodel resolution, JSON/XMI bridge, config, layout
    platform-model-workspace/          Model workspace CRUD, validation, import/export
    platform-artifact-management/      Generated artifact storage and indexing
    platform-mde-pipeline/             MDE pipeline, async jobs, and model synchronization
    platform-modeling-assistant/       AI modeling assistant orchestration and persistence
    platform-project-export/           Project archive ZIP packaging
    platform-storage-postgres/         PostgreSQL adapter and platform Flyway migrations
    epsilon-model-validation/          Reusable Epsilon EVL validation library
    epsilon-model-transformation/      Reusable Epsilon ETL transformation library
    epsilon-text-generation/           Reusable Epsilon EGX/EGL generation library
    emf-compare-standalone/            Standalone packaging of the EMF Compare core bundle
tools/
  emfatic-to-ecore-cli/      Emfatic-to-Ecore compiler CLI
  model-validator-cli/      Validation CLI
  model-transformer-cli/  Transformation CLI
  code-generator-cli/        Generation CLI
  notation-migrate/        Legacy UI-metadata to CVS migration utility (Node.js)
scripts/                   Repository automation (format, lint, verify, Flyway check)
```

## Maven Dependency Direction

Feature libraries depend inward on shared types and the storage port. The backend composes
feature modules plus the Postgres storage adapter. Epsilon execution libraries stay Spring-free and are
reused from CLIs and transformation services.

```text
platform-shared-types
platform-storage-api → platform-shared-types

platform-user-authentication → platform-storage-api
platform-project-management → platform-user-authentication
platform-modeling-infrastructure → platform-shared-types
platform-model-workspace → platform-project-management, platform-modeling-infrastructure, epsilon-model-validation
platform-artifact-management → platform-project-management
platform-mde-pipeline → platform-model-workspace, platform-artifact-management, epsilon-model-transformation, epsilon-text-generation
platform-modeling-assistant → platform-model-workspace, platform-modeling-infrastructure, platform-project-management
platform-project-export → platform-project-management, platform-model-workspace, platform-artifact-management, platform-mde-pipeline

platform-storage-postgres → platform-storage-api + all feature domain types

apps/backend → platform-storage-postgres + platform-modeling-assistant + platform-project-export + feature modules
```

This separation keeps formal MDE execution reusable outside the web backend and isolates
PostgreSQL behind `PlatformStore`.

## Canonical Documentation

The repository also contains a detailed Mermaid diagram package in `docs/diagrams/`, a checked-in
OpenAPI contract in `docs/api/openapi/openapi.yaml`, storage and assistant guides under
`docs/internal/`, generated-artifact operations guides, and module-specific READMEs.
