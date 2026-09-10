# CIM concrete visual syntax

This document records the concrete visual syntax (CVS) decisions for the CIM
DSML. It is intentionally limited to CIM; PIM and PSM are separate design
efforts.

The configuration is implemented in
[`mde/notation/cim.cvs.json`](../mde/notation/cim.cvs.json). The decisions below
are based on the Ecore generated from the eight CIM Emfatic modules and the
shared kernel, not on the names that happened to exist in the previous CVS.

## 1. What CIM is for

CIM is the business-facing transformation bridge between a business problem
and later platform-independent and platform-specific designs. It is not one
diagram type. It captures several related but different questions:

1. What outcomes, requirements, stakeholders, and capabilities matter?
2. Where are the domain boundaries, entities, values, aggregates, and data?
3. Which commands, queries, events, errors, and policies express business
   behavior?
4. Which end-to-end business processes coordinate that behavior?
5. Which quality, security, privacy, compliance, risk, and transformation
   decisions constrain the model?

The CVS therefore uses multiple focused viewpoints. A user can move from a
high-level map to a container focus without putting every CIM object on one
canvas.

## 2. Metamodel interpretation

### Root and organization

`CIMModel` is the root aggregate of the serialized model. It owns the root
collections for requirements, goals, KPIs, stakeholders, actors, roles,
capabilities, bounded-context candidates, domain concepts, behavior, process
and policy artifacts, governance concerns, risks, transformation settings,
traceability, and readiness.

The organization package expresses intent and responsibility:

- `Requirement` is the authoritative statement of need. Its acceptance
  criteria are nested details, while support, constraint, dependency, conflict,
  refinement, and derivation are relationships.
- `BusinessGoal` and `KPI` express outcomes and measurement. A KPI is a
  measurement detail of a goal, not a general-purpose domain node.
- `Stakeholder`, `Actor`, `Role`, and `ExternalSystem` identify who owns,
  initiates, authorizes, participates in, or exchanges information with the
  business.
- `BusinessCapability` describes a stable business responsibility. Its links to
  goals, requirements, actors, processes, entities, commands, queries, and
  events are semantic associations; they are not nested ownership of all those
  objects.
- `BoundedContextCandidate` groups ubiquitous-language terms and provides a
  candidate language/ownership/integration boundary. Its links to capabilities,
  entities, commands, queries, events, and policies are membership references,
  not Ecore containment.

### Domain and data

The domain-data package separates identity-bearing concepts from descriptive
values and information:

- `DomainEntity` has identity, lifecycle states, invariants, and information
  attributes.
- `ValueObject` is immutable and compared by selected equality attributes.
- `AggregateCandidate` identifies a consistency boundary, its root entity,
  members, handled commands, and emitted events.
- `DomainRelationship` is a first-class relationship object. Its relationship
  type and multiplicities carry meaning that should remain visible on a
  connector.
- `InformationItem` describes business data, including type, cardinality,
  derivation, source of truth, retention, audit, and search/reporting concerns.
  Its nested `subItems` are the only information structure that should be
  opened as a child canvas.
- `DataClassification` and the privacy/compliance references capture data
  governance. They are generally better as badges, links, and inspector values
  than as large nodes on every domain diagram.

### Behavior

The behavior package follows the command/query/event distinction:

- `Command` represents an intent that may change business state. Its target,
  preconditions, expected/rejection events, possible errors, authorization,
  and idempotency are important semantics.
- `Query` represents an information request. It reads domain entities and has
  explicit output, freshness, pagination, filtering, sorting, and
  confidentiality expectations.
- `BusinessEvent` is a past-tense business fact. It has semantic naming,
  payload, time semantics, versioning, causation/correlation candidates, and
  consumers.
- `BusinessError` is a business-visible rejection or failure, not merely a
  technical exception.
- `Condition` is a predicate used by commands, policies, decisions, and
  processes.

`CommandOutcome` is nested under a command. The input/output/payload
references are intentionally edited in the inspector because they can be
high-cardinality and would otherwise make event-storming canvases unreadable.

### Process and policy

`BusinessProcess` owns ordered `ProcessStep` instances, transitions, exception
scenarios, and temporal constraints. The concrete step classes provide the
visual language of the process editor: start, end, command, query, event,
policy, human task, external interaction, decision, and wait.

`ProcessTransition` is a first-class edge object between steps. It is the
correct place for labels, branch conditions, order, and probability hints.

`Policy`, `DecisionTable`, and `DecisionRule` describe business rules and
decisions. A policy can react to events, guard commands, constrain queries,
emit commands/events, and use a decision table. Decision rules are rows inside
a decision table, not independent model nodes.

### Governance, transformation, and kernel

Governance specializes requirements into non-functional, security, privacy,
and compliance constraints. `Risk`, `Assumption`, and `Hotspot` capture
uncertainty and transformation blockers. `TransformationProfile` stores
cross-cutting transformation preferences.

The shared kernel supplies traceability, expressions, multiplicities,
annotations, readiness findings/checks, and manual decisions. These are
supporting model structures. They are available through their owner or a
dedicated overlay rather than being mixed into every modeling palette.

## 3. Viewpoint design

Every configured view has identical raw `palette` and `canvas` lists. This
keeps the objects a user can add consistent with the objects the view is meant
to show. Relationship objects are deliberately absent from palettes: users
create them by connecting endpoints, while the backend stores the appropriate
relationship object.

| View                       | Canvas population                                                                                                                                                      | Primary purpose                                                                                                                                             |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CIM Overview               | Goals, requirements, capabilities, contexts, participants, domain anchors, behavior anchors, processes, policies, risks, hotspots, transformation profile, trace model | A deliberately small orientation map. Detailed constraint and data-governance objects have their own view.                                                  |
| Goals and Requirements     | `Requirement`, `BusinessGoal`, `KPI`, `Stakeholder`, NFR/security/privacy/compliance constraints                                                                       | Turn needs into measurable outcomes and expose support, refinement, dependency, conflict, and constraint links.                                             |
| Capability                 | `BusinessCapability`, `BoundedContextCandidate`, `Actor`, `BusinessGoal`                                                                                               | Map stable responsibilities to outcomes, owners, contexts, and dependency structure.                                                                        |
| Domain Model               | `DomainEntity`, `ValueObject`, `InformationItem`, `DataClassification`, `AggregateCandidate`                                                                           | Model identity, values, data, aggregate anchors, classification, and explicit domain relationships.                                                         |
| Business Process           | `BusinessProcess`, `BusinessCapability`, `Actor`, `Command`, `BusinessEvent`                                                                                           | Catalog processes and expose their business trigger/ownership context; open a process for its executable flow.                                              |
| Stakeholder and Actor      | Stakeholders, actors, systems, roles, goals, requirements, commands, queries, events, information items                                                                | Make responsibility, participation, and external exchange understandable without opening unrelated domain detail.                                           |
| Aggregate                  | `AggregateCandidate`, `DomainEntity`, `Command`, `BusinessEvent`, `Policy`                                                                                             | Validate the proposed consistency boundary against roots, members, commands, events, and policies. Open the entity to edit lifecycle states and invariants. |
| Behavior                   | Commands, queries, events, errors, conditions, actors, capabilities, aggregates, information items                                                                     | Explore intent-to-fact behavior and the key actor/target/data associations. Outcomes remain inside commands.                                                |
| Policy and Decision        | Policies, decision tables, conditions, commands, queries, events, information items                                                                                    | Relate rules and decisions to the behavior they guard, trigger, constrain, or produce. Open a decision table for its rules.                                 |
| Governance and Risk        | NFRs, security/privacy/compliance constraints, risks, assumptions, hotspots, classifications, transformation profile, readiness, trace model                           | Review constraints and uncertainty while keeping them out of the main domain/process canvases.                                                              |
| Traceability and Readiness | Trace model, readiness assessment, transformation profile, risks, assumptions, hotspots                                                                                | Review transformation evidence and blockers as a separate concern. Trace links remain a distinct overlay.                                                   |

The overview is not the default editing surface for every class. It is a
navigation surface. The specialized views are where users should create and
connect concepts.

## 4. Container and nested-palette policy

Only genuine, mutable Ecore containment is treated as a child canvas. This
avoids making a reference such as `BoundedContextCandidate.entities` look like
ownership when it is actually a cross-object membership link.

| Container                  | Palette/canvas children                                           | Why it is opened                                                                                          |
| -------------------------- | ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `Requirement`              | `AcceptanceCriterion`                                             | Acceptance criteria refine one requirement and are not useful as global nodes.                            |
| `BoundedContextCandidate`  | `UbiquitousLanguageTerm`                                          | Terms form the language glossary of one boundary.                                                         |
| `Command`                  | `CommandOutcome`                                                  | Outcomes explain success, emitted events, errors, and output without flooding the behavior view.          |
| `DomainEntity`             | `LifecycleStateDefinition`, `BusinessInvariant`                   | Lifecycle and invariants are entity-local detail.                                                         |
| `ValueObject`              | `InformationItem`                                                 | Value-object fields are structural detail; the value object remains the domain-level node.                |
| `AggregateCandidate`       | `BusinessInvariant`                                               | Boundary invariants belong to the aggregate focus. Root/member/command/event links remain semantic edges. |
| `InformationItem`          | `InformationItem`                                                 | Recursive `subItems` are the one data-structure hierarchy that benefits from nesting.                     |
| `NonFunctionalRequirement` | `QualityScenario`                                                 | Scenarios operationalize one quality requirement.                                                         |
| `BusinessProcess`          | Concrete process steps, `ExceptionScenario`, `TemporalConstraint` | These artifacts form the executable process canvas. `ProcessTransition` is drawn as an edge.              |
| `DecisionTable`            | `DecisionRule`                                                    | Rules are table rows and should not be placed on global canvases.                                         |

The process profile also retains `DecisionTable` as a related-artifact
shortcut in the process container palette. A decision table is _not_ a child
of `BusinessProcess` in the metamodel: it is root-contained and referenced by
`DecisionStep`. The shortcut exists so a user designing a decision step can
reach the table concept from the process context; persisted ownership remains
the root `decisionTables` collection and the `DecisionStep.decisionTable`
reference.

Expressions, multiplicities, annotations, readiness findings/checks, and
manual decisions are kernel/support details. They remain accessible through
the attribute/inspector experience and are not draggable semantic concepts.

## 5. Edge versus inspector strategy

The guiding rule is to draw an edge only when direction and topology answer a
real modeling question. A value that is descriptive, high-cardinality,
technical, or naturally edited as a property stays in the inspector.

### Always edge objects

These EClasses are relationship objects and are rendered as connectors:

- `RequirementRelationship`: dependency, conflict, refinement, duplicate, or
  derivation between requirements.
- `DomainRelationship`: association, composition, aggregation, generalization,
  dependency, or ownership between domain concepts. Its type controls the
  connector marker.
- `CapabilityDependency`: dependency between capabilities, including critical
  path and reason.
- `ProcessTransition`: control flow between process steps.
- `TraceLink`: traceability between arbitrary model elements, rendered in a
  separate purple dashed layer.

### Exposed semantic reference edges

The CVS maps selected EReferences to canonical relationship kinds. Important
examples are:

- goals and requirements: `SUPPORTS`, `CONSTRAINS`, `MEASURED_BY`,
  `REFINED_BY`, `OWNS_GOALS`, `PROVIDES_REQUIREMENTS`, and
  `REALIZES_REQUIREMENTS`;
- capability/context structure: `OWNER`, `OWNS_PROCESSES`, `MANAGES`,
  `CONSTRAINED_BY`, `MEMBER`, `ROOT`, `CONTEXT`, and the explicit
  `CONTAINS_COMMAND`, `CONTAINS_QUERY`, and `CONTAINS_EVENT` semantic links;
- behavior: `TARGET_CAPABILITY`, `TARGET_AGGREGATE`, `PRECONDITIONS`,
  `EXPECTED_EVENTS`, `REJECTION_EVENTS`, `POSSIBLE_ERRORS`, `READS`,
  `PAYLOAD`, and `AFFECTS`;
- process/policy: `TRIGGERED_BY`, `USES_COMMAND`, `USES_QUERY`,
  `USES_EVENT`, `USES_POLICY`, `RESPONSIBLE_ROLES`, `GUARDED_BY`,
  `DECISION_TABLE`, `RESULTING_COMMANDS`, and `RESULTING_EVENTS`;
- governance: `CONSTRAINED_ELEMENTS`, `CONSTRAINED_ACTORS`,
  `CONSTRAINED_COMMANDS`, `CONSTRAINED_QUERIES`,
  `CONSTRAINED_INFORMATION`, `DATA_ITEMS`, `DATA_SUBJECTS`,
  `SCOPED_ELEMENTS`, and `ATTACHED_TO`.

Inverse EReferences are excluded when the forward reference is the clearer
direction. For example, actor-to-command `ISSUES_COMMANDS` is preferred over
the inverse command-to-actor `issuedBy`. This prevents duplicate parallel
edges and makes diagrams read naturally.

### Inspector-only information

The inspector remains the authoritative editing surface for:

- command/query input and output lists, event payload details, and information
  item attributes;
- entity identity attributes, primary identity, value-object equality
  attributes, and source-of-truth/retention/validation properties;
- multiplicities, expressions, policy text, authorization rules, and temporal
  expressions;
- inverse references and technical kernel references;
- high-degree references such as generic affected-element lists, which would
  create dense hairball diagrams.

This is not a loss of semantics. The relationships remain serialized in the
model and are exposed in the selected element's reference/attribute editor;
the CVS only decides whether they receive a canvas connector in a given view.

## 6. Visual language and complexity controls

The notation rules use a consistent semantic vocabulary:

- blue cards and edges for goals/requirements and strategy;
- violet participant cards for stakeholders, actors, roles, and systems;
- teal containers/edges for capabilities, boundaries, and business structure;
- cyan class/field cards for domain and data concepts;
- red hexagons/octagons for commands and business errors;
- pink hexagons for business events;
- orange process steps and control flow;
- amber policy/governance cards and constraint edges;
- purple dashed trace edges.

Commands, queries, events, errors, conditions, starts, and ends also have
distinct primitive shapes. This lets users read a behavior or process diagram
by shape before opening the inspector.

Complexity is managed through:

1. named question-specific views;
2. nested focus for real containment only;
3. semantic zoom and compact high-signal line fields;
4. edge-layer filtering by strategy, behavior, governance, containment, and
   trace semantics;
5. inspector editing for high-cardinality and technical references;
6. search, relationship exploration, and trace/readiness overlays.

The result is a syntax that supports natural modeling gestures, create a
concept from the relevant palette, open true local detail, and draw a
meaningful relationship, without turning every reference into a connector or
every nested support object into a first-class node.

## 7. Verification and scope boundary

The updated CVS is loaded and normalized against the combined CIM Ecore
metamodel. Focused verification covers element/view metadata, palette and
containment behavior, relationship-rule derivation, and CIM XMI import.

Semantic EVL validation remains an explicit model-validation workflow. The
chatbot assistant's generated model actions are limited to structural Ecore/
EMF conformance and must not invoke semantic EVL validation as an apply/repair/
commit gate.
