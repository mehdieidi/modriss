# Modless Diagram Package

This directory is the diagram source of truth for the implemented Modless repository. Every
diagram is Mermaid embedded in Markdown so it renders in GitHub, compatible IDE previews, and
Mermaid tooling.

The diagrams were derived from:

- Spring controllers, configuration, services, and assistant classes under `apps/backend`.
- Browser client modules under `apps/frontend/js`.
- Maven module POMs, `deploy/compose.yaml`, and backend configuration.
- `PlatformStore`, `PostgresPlatformStore`, and Flyway migrations under
  `platform-storage-postgres` (V1 platform schema, V5 MDE job metadata) and `platform-assistant`
  (V2–V4 and V6–V8 assistant schema).
- Emfatic/Ecore metamodels, EVL entry modules, ETL entry modules, and EGX/EGL generation assets.

## Diagram Index

| Area          | File                                                                                             | Coverage                                           |
| ------------- | ------------------------------------------------------------------------------------------------ | -------------------------------------------------- |
| C4            | [01-c4-system-context.md](01-c4-system-context.md)                                               | Users, external systems, Modless boundary          |
| C4            | [02-c4-containers.md](02-c4-containers.md)                                                       | Runtime containers and protocols                   |
| C4            | [03-c4-backend-components.md](03-c4-backend-components.md)                                       | Backend components and ports/adapters              |
| Architecture  | [04-deployment-and-runtime.md](04-deployment-and-runtime.md)                                     | Docker/runtime deployment and traffic              |
| Dependencies  | [05-module-dependencies.md](05-module-dependencies.md)                                           | Maven, browser, MDE, and external dependencies     |
| Classes       | [06-core-class-relations.md](06-core-class-relations.md)                                         | Major backend/application class relations          |
| Storage       | [07-platform-storage-er.md](07-platform-storage-er.md)                                           | Full platform persistence ERD                      |
| Storage       | [08-assistant-storage-er.md](08-assistant-storage-er.md)                                         | Full assistant persistence ERD                     |
| APIs          | [09-api-auth-project-sequences.md](09-api-auth-project-sequences.md)                             | Auth and project endpoint sequences                |
| APIs          | [10-api-model-sequences.md](10-api-model-sequences.md)                                           | Every model endpoint sequence                      |
| APIs          | [11-api-transformation-artifact-sequences.md](11-api-transformation-artifact-sequences.md)       | Transformation, job, artifact sequences            |
| APIs          | [12-api-modeling-system-sequences.md](12-api-modeling-system-sequences.md)                       | Layout, config, health, docs, future routes        |
| APIs          | [13-api-assistant-sequences.md](13-api-assistant-sequences.md)                                   | Every chatbot, SSE, and WebSocket sequence         |
| AI assistant  | [14-ai-assistant-architecture.md](14-ai-assistant-architecture.md)                               | Assistant components, providers, context, controls |
| AI assistant  | [15-ai-assistant-turn-and-proposal.md](15-ai-assistant-turn-and-proposal.md)                     | Turn orchestration and proposal lifecycle          |
| AI assistant  | [16-ai-assistant-rag-and-memory.md](16-ai-assistant-rag-and-memory.md)                           | Catalog indexing, retrieval, context, memory       |
| MDE           | [17-mde-architecture.md](17-mde-architecture.md)                                                 | MDE assets, runners, services, and tools           |
| MDE           | [18-mde-end-to-end-pipeline.md](18-mde-end-to-end-pipeline.md)                                   | CIM to PIM to AWS PSM to artifacts                 |
| MDE           | [19-mde-validation-transformation-generation.md](19-mde-validation-transformation-generation.md) | EVL, ETL, and EGX execution internals              |
| MDE           | [20-metamodel-relations.md](20-metamodel-relations.md)                                           | Shared kernel and CIM/PIM/PSM package relations    |
| Frontend      | [21-frontend-architecture-and-flows.md](21-frontend-architecture-and-flows.md)                   | Browser modules and primary user flows             |
| Functions     | [22-major-function-flows.md](22-major-function-flows.md)                                         | Important service algorithms and state machines    |
| Cross-cutting | [23-security-observability-failure.md](23-security-observability-failure.md)                     | Security boundaries, logging, failure paths        |

## API Coverage

Every implemented controller route template has a sequence diagram:

- Auth: 5 routes.
- Projects and membership: 9 routes.
- Models, validation, import, and export: 11 parameterized route templates.
- Transformations and MDE jobs: 5 routes.
- Artifacts: 5 routes.
- Modeling configuration and layout: 3 routes.
- Assistant REST/SSE: 9 routes.
- Assistant WebSocket: 1 route.
- Health, OpenAPI, Swagger UI, future-feature wildcard routes, and Actuator endpoints.

`{level}` means one of `cim`, `pim`, or `psm`; the same sequence applies to each concrete level.

## Reading Conventions

- Solid arrows are synchronous calls or required dependencies.
- Dotted arrows are responses, events, generated outputs, or optional/fallback paths.
- PostgreSQL table names are uppercase in ER diagrams only to improve readability.
- “PlatformStore” means the logical application persistence port; production maps it to
  `PostgresPlatformStore`.
- Transformation REST routes submit work through `MdeJobService` and return `202 Accepted` with a
  `Location` header. Clients poll `/api/transformations/jobs/{id}` until the job completes or
  fails. Job cancellation is available through `POST /api/transformations/jobs/{id}/cancel`.
