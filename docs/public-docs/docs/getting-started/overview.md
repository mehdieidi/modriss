# Overview

## Intended Users

MODRISS serves several related roles:

- **Domain experts** describe goals, actors, concepts, policies, processes, and constraints at CIM.
- **Architects** refine the design into provider-independent serverless responsibilities at PIM.
- **Cloud engineers** review AWS-specific resources and generated projects at PSM.
- **Developers** implement protected business-logic regions and test generated code.
- **Researchers and language engineers** evolve metamodels, constraints, transformations, and
  generation rules.

## Main Runtime Components

| Component    | Technology                           | Default address         | Responsibility                        |
| ------------ | ------------------------------------ | ----------------------- | ------------------------------------- |
| Frontend     | Plain HTML, CSS, JavaScript, AntV G6 | `http://127.0.0.1:8082` | Visual modeling, assistant, artifacts |
| Backend      | Java 17, Spring Boot                 | `http://127.0.0.1:8080` | API, orchestration, persistence, AI   |
| PostgreSQL   | PostgreSQL 16 with pgvector          | `localhost:5432`        | Platform and assistant state          |
| Landing site | Static HTML/CSS/JS                   | `http://127.0.0.1:8083` | Product introduction                  |
| Floci        | AWS service emulator (default)       | `http://127.0.0.1:4566` | Generated-project deployment testing  |
| LocalStack   | Alternate AWS service emulator       | `http://127.0.0.1:4566` | Compatibility/comparison testing      |
| Dozzle       | Container log viewer                 | `http://127.0.0.1:9999` | Development log inspection            |

## Typical Lifecycle

1. Register or log in.
2. Create a project.
3. Create a CIM model or import the included sample.
4. Model business intent and validate it.
5. Transform CIM to PIM and review the generated architecture.
6. Transform PIM to AWS PSM and review provider-specific resources.
7. Generate an AWS project from PSM.
8. Browse, edit, and download the generated artifact.
9. Validate and deploy it to the selected Floci/LocalStack emulator or a controlled AWS environment.

Each model is stored with a revision. Updates and transformations use optimistic concurrency so a
client cannot silently overwrite a newer revision.

## Formal Sources of Truth

| Concern                                      | Source                                                         |
| -------------------------------------------- | -------------------------------------------------------------- |
| Shared and level-specific language structure | `mde/metamodels/**/*.emf`                                      |
| Runtime metamodel structure                  | `mde/metamodels/**/*-combined.ecore`                           |
| Semantic constraints                         | `mde/validation/`                                              |
| CIM-to-PIM transformation                    | `mde/transformations/cim-to-pim/`                              |
| PIM-to-AWS-PSM transformation                | `mde/transformations/pim-to-awspsm/`                           |
| PSM-to-artifact generation                   | `mde/generation/awspsm-to-artifacts/`                          |
| Visual syntax and palettes                   | `packages/java/platform-modeling/src/main/resources/modeling/` |
| Public backend behavior                      | Backend controllers and checked-in OpenAPI contract            |
