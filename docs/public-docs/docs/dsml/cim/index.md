# CIM DSML

The Computation-Independent Model (CIM) is the business-facing layer of MODRISS. It records what an organisation needs to achieve, the people and systems involved, the concepts used in its domain, and the rules that govern business behaviour. It deliberately postpones decisions about functions, APIs, databases, cloud services, and deployment topology.

That separation is useful only when the CIM is precise. A name such as `Order` is insufficient on its own. The model should explain what an order means in this domain, how it is identified, which lifecycle it follows, which capability owns it, what information it carries, what can happen to it, and which constraints apply. The CIM metamodel provides separate concepts for these questions so that later transformations do not have to infer all of them from free text.

## CIM in the MODRISS framework

MODRISS is an academic model-driven framework for serverless software development. The landing site describes the framework as a combination of an explicit development process and a modeling framework. The modeling framework uses Ecore metamodels for abstract syntax, EVL for semantic checks, ETL for model refinement, and EGX/EGL for generation. CIM is the first modeling level in the refinement path:

```text
business understanding
        │
        ▼
CIM: domain intent and business semantics
        │  CIM-to-PIM ETL, with trace links and manual decisions
        ▼
PIM: platform-independent serverless architecture
        │  PIM-to-AWS-PSM ETL
        ▼
AWS PSM: provider-specific resources and configuration
        │  EGX/EGL model-to-text generation
        ▼
reviewable implementation artefacts
```

CIM-to-PIM refinement is semi-automated. The transformation can create an initial service, function, contract, workflow, data, policy, security, and integration design from CIM facts. It cannot decide every business boundary safely. `Risk`, `Assumption`, `Hotspot`, `TransformationProfile`, `ManualDecision`, and the shared trace model keep those unresolved choices visible.

The CIM language is also independent of the assistant. The LLM-based assistant may help a modeler create, inspect, explain, or modify a CIM model, but the live Ecore metamodel remains the structural contract. Assistant-generated model output is checked for structural Ecore/EMF conformance. EVL semantic validation belongs to an explicit user-initiated validation workflow outside assistant apply, repair, and commit paths.

## How to read the CIM reference

The eight `.emf` files are combined into `mde/metamodels/cim/cim-combined.ecore`. The `.emf` declarations are the abstract-syntax authority. EVL adds semantic rules and critiques; it does not add classes or features to the metamodel. ETL and EOL show which CIM features influence refinement and how the transformation resolves or records uncertainty.

In the relationship tables:

- `containment` means that the source object owns the target object in the EMF object tree. Deleting the source removes the contained object from that model subtree.
- `reference` means that the source points to an independently owned object. A reference can connect several parts of the model to the same concept.
- `0..1`, `1`, `0..*`, and `1..*` are the compiled Ecore lower and upper bounds. An optional scalar attribute is `0..1`, even when its type is `Boolean` or an enumeration.
- An `opposite` is the reverse end of the same bidirectional Ecore reference. It is not a second business relationship.
- A `val` declaration in Emfatic becomes a containment. A `ref` declaration becomes a non-containment reference unless the source explicitly declares containment through another construct.

Every CIM element also inherits the shared kernel. `ModelElement` supplies the stable `id`, user-facing name, explanations, tags, lifecycle status, annotations, and trace links. `TraceableElement` adds source evidence, rationale, review information, and transformation provenance. These inherited features are documented in the [shared kernel reference](../shared-kernel.md).

## Module map

| Module                   | What it contributes                                                                                                                        | Reference                                                |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------- |
| `cim-root.emf`           | The root of one CIM model and its containment tree.                                                                                        | [CIM model root](cim-root.md)                            |
| `cim-organization.emf`   | Requirements, goals, stakeholders, actors, roles, capabilities, bounded-context candidates, and business language.                         | [Organization and bounded contexts](cim-organization.md) |
| `cim-domain-data.emf`    | Domain concepts, entities, value objects, relationships, aggregates, information items, lifecycle states, invariants, and classifications. | [Domain and data](cim-domain-data.md)                    |
| `cim-behavior.emf`       | Commands, queries, events, business errors, and conditions.                                                                                | [Behavior](cim-behavior.md)                              |
| `cim-process-policy.emf` | Processes, typed process steps, transitions, policies, decision tables, exceptions, and temporal constraints.                              | [Processes and policies](cim-process-policy.md)          |
| `cim-governance.emf`     | Quality scenarios, security, privacy, and compliance constraints.                                                                          | [Governance](cim-governance.md)                          |
| `cim-transformation.emf` | Risks, assumptions, hotspots, and preferences that guide CIM-to-PIM refinement.                                                            | [Transformation metadata](cim-transformation.md)         |
| `cim-types.emf`          | The controlled vocabularies used by the other CIM modules.                                                                                 | [Enumerations](cim-types.md)                             |

## A sensible modeling order

There is no requirement that a modeler fill every class in a single pass. A practical increment usually begins with the model scope, goals, actors, and capabilities. Requirements and acceptance criteria then make the intended outcome testable. Domain entities, information items, commands, queries, and events give that intent a business vocabulary. Processes, policies, and governance constraints add sequencing and obligations. Finally, the modeler records transformation preferences, open decisions, risks, and readiness evidence.

The order matters because the concepts reinforce one another. A command becomes meaningful when it has an issuer, a target capability or aggregate, input information, expected outcomes, and possible errors. A privacy constraint becomes useful when its `dataItems` identify the information it governs and its `purpose` and `legalBasis` explain why that processing is allowed. A process transition becomes reviewable when it connects two distinct steps and states the condition under which the path is taken.

## Semantic validation and refinement

The CIM EVL entry point is `mde/validation/cim/cim-semantic-validation.evl`. It imports rules for core traceability, organization intent, domain data, behavior, process and policy, and governance/readiness. Constraints report semantic errors. Critiques report warnings about weak explanation, missing ownership, incomplete traceability, or insufficient transformation readiness.

The CIM-to-PIM entry point is `mde/transformations/cim-to-pim/cim-to-pim.etl`. Its rules are divided by concern. Business capabilities and bounded contexts seed services; actors and roles seed principals; entities and aggregates seed schemas and data stores; commands and queries seed functions and routes; events seed event types and flows; processes and policies seed workflows and policy functions; governance elements seed security, privacy, compliance, resilience, observability, and data-protection policies. The transformation also creates trace links and readiness findings so that a generated PIM remains explainable.

## Additional resources

- [CIM modeling methodology](../../guides/cim-modeling-methodology.md)
- [CIM-to-PIM transformation overview](../../transformations/cim-to-pim/index.md)
- [CIM EVL validation overview](../../evl/index.md)
- [Shared kernel reference](../shared-kernel.md)
- [Sample CIM model](https://github.com/mehdieidi/modriss/blob/main/mde/samples/cim.xmi)
