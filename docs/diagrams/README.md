# MODRISS Diagram Package

This directory is the diagram source of truth for the implemented MODRISS repository. Most
diagrams are Mermaid embedded in Markdown so they render in GitHub, compatible IDE previews, and
Mermaid tooling; standalone SVGs are used where a polished documentation graphic is needed.

The diagrams were derived from:

- Spring controllers, configuration, services, and assistant classes under `apps/backend` and
  feature modules under `packages/java`.
- Browser client modules under `apps/frontend/js`.
- Maven module POMs, `deploy/compose.base.yaml` and its environment overlays, and backend configuration.
- `PlatformStore`, `PostgresPlatformStore`, and Flyway migrations under
  `platform-storage-postgres` (including V32 model synchronization records) and `platform-assistant`
  (V14 durable-turn baseline and later assistant migrations through V31).
- Emfatic/Ecore metamodels, EVL entry modules, ETL entry modules, and EGX/EGL generation assets.

## Diagram Index

| Area          | File                                                                                             | Coverage                                           |
| ------------- | ------------------------------------------------------------------------------------------------ | -------------------------------------------------- |
| C4            | [01-c4-system-context.md](01-c4-system-context.md)                                               | Users, external systems, MODRISS boundary          |
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
| APIs          | [13-api-assistant-sequences.md](13-api-assistant-sequences.md)                                   | Chatbot, durable turn, and authenticated SSE flows |
| AI assistant  | [14-ai-assistant-architecture.md](14-ai-assistant-architecture.md)                               | Unified router, conceptual/compiler, agent, safety |
| AI assistant  | [15-ai-assistant-turn-and-proposal.md](15-ai-assistant-turn-and-proposal.md)                     | Adaptive durable turn and checkpoint lifecycle     |
| AI assistant  | [16-ai-assistant-rag-and-memory.md](16-ai-assistant-rag-and-memory.md)                           | Ecore contracts, source units, provenance, memory  |
| MDE           | [17-mde-architecture.md](17-mde-architecture.md)                                                 | MDE assets, runners, services, and tools           |
| MDE           | [18-mde-end-to-end-pipeline.md](18-mde-end-to-end-pipeline.md)                                   | CIM to PIM to AWS PSM to artifacts                 |
| MDE           | [19-mde-validation-transformation-generation.md](19-mde-validation-transformation-generation.md) | EVL, ETL, and EGX execution internals              |
| MDE           | [20-metamodel-relations.md](20-metamodel-relations.md)                                           | Shared kernel and CIM/PIM/PSM package relations    |
| Frontend      | [21-frontend-architecture-and-flows.md](21-frontend-architecture-and-flows.md)                   | Browser modules and primary user flows             |
| Functions     | [22-major-function-flows.md](22-major-function-flows.md)                                         | Important service algorithms and state machines    |
| Cross-cutting | [23-security-observability-failure.md](23-security-observability-failure.md)                     | Security boundaries, logging, failure paths        |
| Methodology   | [24-cim-methodology.md](24-cim-methodology.md)                                                   | CIM phase flow, RACI, coverage heatmap             |
| Methodology   | [25-pim-methodology.md](25-pim-methodology.md)                                                   | PIM phase flow and ETL alignment                   |
| Methodology   | [26-psm-methodology.md](26-psm-methodology.md)                                                   | PSM phase flow and SamStack hub                    |
| Methodology   | [27-end-to-end-methodology.md](27-end-to-end-methodology.md)                                     | CIM→PIM→PSM pipeline with EVL/ETL gates            |
| DevOps/SRE    | [28-devops-sre-runtime.md](28-devops-sre-runtime.md)                                             | Edge routing, workloads, observability, triage     |
| AI assistant  | [29-agents-pattern-assessment.md](29-agents-pattern-assessment.md)                               | Router/prompt-chain/agent pattern mapping          |
| MDE           | [34-mde-modeling-path.svg](34-mde-modeling-path.svg)                                             | CIM→PIM→AWS PSM→generated AWS project baseline     |

## API Coverage

The API sequence diagrams cover the primary controller route templates and interaction flows:

- Auth: 5 routes.
- Projects and membership: 9 routes.
- Models, validation, import, and export: 11 parameterized route templates.
- Transformations and MDE jobs: 5 routes.
- Artifacts: 5 routes.
- Modeling configuration, layout, and process definitions: 7 routes.
- Assistant REST/SSE: CIM/PIM session, message submission, durable turn status, event replay,
  attachment, thread, cancel, continue, confirm, undo, rollback, rebase, feedback, source and
  obligation coverage, legacy proposal lookup/undo, and clear flows.
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
