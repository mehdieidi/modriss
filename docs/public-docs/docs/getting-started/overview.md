# Overview

## Intended Users

MODRISS serves several related roles:

- **Domain experts** describe goals, actors, concepts, policies, processes, and constraints at CIM.
- **Architects** refine the design into provider-independent serverless responsibilities at PIM.
- **Cloud engineers** review provider-specific resources and generated projects at PSM.
- **Developers** implement protected business-logic regions and test generated code.
- **Researchers and language engineers** evolve metamodels, constraints, transformations, and
  generation rules, including uploaded packages for additional PSM targets.

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
6. Select a cloud provider, transform PIM to its PSM, and review provider-specific resources.
7. Generate a project using that provider's artifact templates.
8. Browse, edit, and download the generated artifact.
9. Validate and deploy it using the target provider's tools. The built-in AWS path supports
   the selected Floci/LocalStack emulator or a controlled AWS environment.

AWS is available by default. To add another provider, upload its PSM Ecore metamodel and ZIP
packages of ETL transformation rules and EGX/EGL generation scripts. EVL constraints can be
uploaded for explicit validation. The same CIM and PIM can support multiple provider PSMs
and generated projects in one workspace. See [Custom cloud platforms](../guides/custom-cloud-platforms.md)
for the package contract and the minimal Azure example.

Each model is stored with a revision. Updates and transformations use optimistic concurrency so a
client cannot silently overwrite a newer revision.

## Formal Sources of Truth

The repository paths below define the built-in languages and AWS pipeline. For an added
provider, the project stores its uploaded Ecore metamodel, Epsilon packages, and visual notation.

| Concern                                      | Source                                                                        |
| -------------------------------------------- | ----------------------------------------------------------------------------- |
| Shared and level-specific language structure | `methodology/modeling-framework/metamodels/**/*.emf`                          |
| Runtime metamodel structure                  | `methodology/modeling-framework/metamodels/**/*-combined.ecore`               |
| Semantic constraints                         | `methodology/modeling-framework/validation/`                                  |
| CIM-to-PIM transformation                    | `methodology/modeling-framework/transformations/cim-to-pim/`                  |
| PIM-to-PSM transformation                    | `methodology/modeling-framework/transformations/pim-to-awspsm/`               |
| PSM-to-artifact generation                   | `methodology/modeling-framework/generation/awspsm-to-artifacts/`              |
| Visual syntax and palettes                   | `packages/java/platform-modeling-infrastructure/src/main/resources/modeling/` |
| Public backend behavior                      | Backend controllers and checked-in OpenAPI contract                           |
