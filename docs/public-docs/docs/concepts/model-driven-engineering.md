# Model-Driven Engineering

Modless treats models as executable engineering assets rather than diagrams that merely describe
code. A formal metamodel defines what can exist, constraints define valid meaning, transformations
derive the next abstraction level, and generators produce deployable project assets.

## Core Terms

| Term                          | Meaning in Modless                                                                  |
| ----------------------------- | ----------------------------------------------------------------------------------- |
| Metamodel                     | The formal abstract syntax of a modeling level                                      |
| Model                         | An instance conforming to a metamodel                                               |
| DSML                          | A domain-specific modeling language defined by a metamodel and supporting semantics |
| Structural validation         | Ecore conformance, required features, multiplicities, and references                |
| Semantic validation           | Domain rules implemented in EVL                                                     |
| Model-to-model transformation | ETL rules that derive PIM from CIM or PSM from PIM                                  |
| Model-to-text generation      | EGX/EGL rules that produce files from AWS PSM                                       |
| Traceability                  | Links and reports connecting source concepts, target concepts, and artifacts        |

## Language Stack

- **Emfatic** is the human-authored metamodel syntax.
- **Ecore** is the runtime metamodel representation consumed by EMF and Epsilon.
- **XMI** is the formal serialized model representation used by MDE runners.
- **JSON** is the browser- and API-friendly model representation stored in PostgreSQL.
- **EVL** expresses constraints and critiques.
- **ETL** expresses model-to-model transformations.
- **EOL** provides shared helper logic.
- **EGX/EGL** coordinate and render generated files.

## JSON and XMI

The platform maintains a semantic bridge between frontend JSON models and formal EMF/XMI models.
JSON supports browser editing and persistence. XMI supports metamodel-aware validation,
transformation, and generation.

Imported XMI is staged, converted to platform JSON, and stored with source-XMI metadata. Export can
produce JSON or XMI. A metamodel hash is stored with models so incompatible language changes can be
detected and marked for migration.

## Why Three Levels?

Separating CIM, PIM, and PSM preserves intent while allowing progressively stronger technical
commitments. It avoids mixing provider details into business analysis, and it makes each
transformation boundary reviewable.
