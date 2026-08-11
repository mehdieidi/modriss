# Internal Engineering Documentation

Contributor-focused guides that complement the published MkDocs site (`docs/public-docs/`).
These documents go deeper than the public reference and are aimed at maintainers, thesis
readers, and operators.

## AI Assistant

| Document                                                                                  | Description                                        |
| ----------------------------------------------------------------------------------------- | -------------------------------------------------- |
| [assistant.md](ai/assistant.md)                                                           | Unified architecture, Arvan setup, and guardrails  |
| [assistant-skills.md](ai/assistant-skills.md)                                             | Inspect/contract-path skills and selection rules   |
| [current-llm-workflow.md](ai/current-llm-workflow.md)                                     | Exact adaptive, conceptual, and agent workflows    |
| [conceptual-instance-paper-traceability.md](ai/conceptual-instance-paper-traceability.md) | Full paper-to-implementation traceability          |
| [assistant-approach-comparison.md](ai/assistant-approach-comparison.md)                   | Agent, conceptual, and unified live evidence       |
| [agents-pattern-assessment.md](ai/agents-pattern-assessment.md)                           | Mapping of the adaptive implementation to patterns |
| [live-eval-gate-report.md](ai/live-eval-gate-report.md)                                   | Current four-fixture live-evaluation status        |
| [sample-prompts.md](ai/sample-prompts.md)                                                 | Example prompts for modeling workflows             |
| [implementation-learning-guide.md](ai/implementation-learning-guide.md)                   | End-to-end learning path through the AI stack      |

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
| [concrete-visual-syntax-v2.md](mde/concrete-visual-syntax-v2.md)                             | CVS v2 formalism and migration notes           |

## Configuration and Operations

| Document                                              | Description                                         |
| ----------------------------------------------------- | --------------------------------------------------- |
| [environment-variables.md](environment-variables.md)  | Field-by-field `.env.example` reference             |
| [postgres-storage.md](operations/postgres-storage.md) | PostgreSQL schema, migrations, and storage workflow |
| [docker-logs.md](operations/docker-logs.md)           | Structured logging and Dozzle in the local stack    |

## Project

| Document                                         | Description                                           |
| ------------------------------------------------ | ----------------------------------------------------- |
| [project-description.md](project-description.md) | Thesis-oriented project narrative and roadmap context |
