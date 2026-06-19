# Internal Engineering Documentation

Contributor-focused guides that complement the published MkDocs site (`docs/public-docs/`).
These documents go deeper than the public reference and are aimed at maintainers, thesis
readers, and operators.

## AI Assistant

| Document                                                                | Description                                   |
| ----------------------------------------------------------------------- | --------------------------------------------- |
| [assistant.md](ai/assistant.md)                                         | Assistant architecture, modes, and guardrails |
| [sample-prompts.md](ai/sample-prompts.md)                               | Example prompts for modeling workflows        |
| [implementation-learning-guide.md](ai/implementation-learning-guide.md) | End-to-end learning path through the AI stack |
| [plan.md](ai/plan.md)                                                   | Implementation plan and technology choices    |

## Generated Artifacts

| Document                                                         | Description                                              |
| ---------------------------------------------------------------- | -------------------------------------------------------- |
| [deployment-and-testing.md](artifacts/deployment-and-testing.md) | Deploy and test generated AWS projects                   |
| [debugging-guide.md](artifacts/debugging-guide.md)               | Trace failures back to models, transforms, or generators |

## Model-Driven Engineering

| Document                                                                                     | Description                                    |
| -------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| [change-impact-and-synchronization-guide.md](mde/change-impact-and-synchronization-guide.md) | What to update when metamodels or rules change |
| [concrete-visual-syntax-coverage.md](mde/concrete-visual-syntax-coverage.md)                 | Visual notation coverage notes                 |

## Operations

| Document                                              | Description                                         |
| ----------------------------------------------------- | --------------------------------------------------- |
| [postgres-storage.md](operations/postgres-storage.md) | PostgreSQL schema, migrations, and storage workflow |
| [docker-logs.md](operations/docker-logs.md)           | Structured logging and Dozzle in the local stack    |

## Project

| Document                                         | Description                                           |
| ------------------------------------------------ | ----------------------------------------------------- |
| [project-description.md](project-description.md) | Thesis-oriented project narrative and roadmap context |
