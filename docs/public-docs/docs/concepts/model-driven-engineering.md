# Model-Driven Engineering

MODRISS uses models as part of the engineering work. Each modeling level has a formal metamodel that defines its concepts and relationships. Structural checks verify that a model conforms to this structure. Semantic rules describe additional conditions that people can review through explicit validation. Transformations carry selected information to the next level, and generators use the AWS PSM to create project files.

## Core terms

| Term                              | Meaning in MODRISS                                                                                    |
| --------------------------------- | ----------------------------------------------------------------------------------------------------- |
| **Metamodel**                     | The formal definition of the concepts and relationships available in a modeling language.             |
| **Model**                         | A set of elements that conforms to a metamodel.                                                       |
| **DSML**                          | A domain-specific modeling language whose concepts and semantics are defined for a particular domain. |
| **Structural validation**         | A check of Ecore conformance, required features, multiplicities, and reference integrity.             |
| **Semantic validation**           | A check of domain rules expressed in EVL and run in an explicit user/model validation workflow.       |
| **Model-to-model transformation** | ETL rules that derive PIM from CIM or AWS PSM from PIM.                                               |
| **Model-to-text generation**      | EGX/EGL rules that create files from an AWS PSM.                                                      |
| **Traceability**                  | Links and reports that connect source concepts to refined model elements and generated artifacts.     |

## Language stack

MODRISS uses several Eclipse modeling technologies at different points in the workflow:

- Emfatic is the source notation used to author metamodels.
- Ecore is the runtime metamodel representation used by EMF and Epsilon.
- XMI is a formal model serialization used by the MDE runners.
- JSON is used by the browser and API for model exchange and PostgreSQL persistence.
- EVL expresses semantic constraints and critiques for explicit model validation.
- ETL defines model-to-model transformations, while EOL provides shared helper logic.
- EGX coordinates generation and EGL templates render the resulting files.

## JSON and XMI

The platform translates between browser-facing JSON and formal EMF/XMI models. JSON supports editing and persistence in MODRISS. XMI supports metamodel-aware processing by the MDE runners.

An imported XMI model is staged, converted to the platform representation, and stored with its source-XMI metadata. Models can be exported in JSON or XMI. A metamodel hash helps detect when a model was created against an incompatible language version and may need migration.

## Why the model has three levels

CIM, PIM, and AWS PSM keep business intent separate from architectural and provider-specific decisions. CIM describes the problem and its domain. PIM defines a serverless architecture without committing to a cloud provider. AWS PSM makes the provider-specific design explicit. Each transformation boundary gives the team a point to review what carried forward and what still needs a decision.
