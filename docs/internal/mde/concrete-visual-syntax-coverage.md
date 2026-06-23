# Concrete Visual Syntax Coverage

## Purpose

This document summarizes how the CIM, PIM, and AWS PSM metamodels are presented in the visual
modeling editor. The goal is complete abstract-syntax coverage without forcing every EClass onto one
unreadable canvas.

The abstract syntax remains defined by the Emfatic sources and combined Ecore metamodels. Visual
syntax is configured in:

- `mde/notation/cim.cvs.json` (CVS v2, primary for CIM when present)
- `mde/notation/pim.cvs.json` (phase 2)
- `mde/notation/psm.cvs.json` (phase 3)
- `packages/java/platform-modeling/src/main/resources/modeling/cim-ui-metadata.json` (G6 v1 fallback)
- `packages/java/platform-modeling/src/main/resources/modeling/pim-ui-metadata.json`
- `packages/java/platform-modeling/src/main/resources/modeling/psm-ui-metadata.json`

See also `docs/internal/mde/concrete-visual-syntax-v2.md` for the CVS v2 formalism.

The backend merges this metadata with Ecore and exposes the complete result through
`GET /api/modeling/config`. The frontend does not maintain separate type lists for visual roles,
containers, colors, or semantic shapes.

## Universal Coverage Rules

| Abstract-syntax construct                                                    | Concrete syntax                                                     |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Concrete EClass representing an independently meaningful concept or resource | Canvas node/card                                                    |
| EClass owning important containment features                                 | Openable container node with a semantic detail view                 |
| EClass extending `SemanticRelationship`                                      | Semantic edge with its own editable attributes                      |
| Non-containment `ref`                                                        | Legal labeled edge; editing the edge updates the reference          |
| Containment `val`                                                            | Contained detail, container membership, or semantic drill-down      |
| EAttribute                                                                   | Typed inspector field; important fields may also appear on the card |
| EEnum                                                                        | Select field using Ecore literals                                   |
| Many-valued attribute/reference                                              | Multi-select, structured field, table, or tree editor               |
| Abstract/interface/support EClass                                            | Not directly creatable; used for legal-target and inheritance rules |
| Kernel annotation/expression/document structures                             | Inspector/detail syntax rather than ordinary canvas nodes           |
| `TraceModel` / `TraceLink`                                                   | Trace container and dashed trace edge                               |

Every EClass receives a `visualRole`:

- `node`: independently meaningful canvas concept.
- `container`: openable node owning a semantic subgraph.
- `relationship`: semantic edge object.
- `detail`: contained row, table/tree item, structured panel, or inspector object.
- `support`: abstract, technical, or inherited contract not directly created by users.

## CIM Syntax

CIM communicates business meaning and discovery decisions. Its visual language therefore favors
recognizable domain and event-storming notation over implementation detail.

| Role                | Main choices and examples                                                                                                                                                         |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Containers          | `BusinessCapability`, `BoundedContextCandidate`, `AggregateCandidate`, and `BusinessProcess` are openable context/process containers.                                             |
| Concept nodes       | Goals, requirements, actors, domain entities, value objects, commands, queries, events, policies, risks, assumptions, and constraints are cards or meaning-specific nodes.        |
| Behavioral notation | Cards carry configured semantic-shape glyphs: commands use lozenges, queries trapezoids, events hexagons, errors octagons, decisions diamonds, and start/end concepts circles.    |
| Semantic edges      | `DomainRelationship`, `CapabilityDependency`, `RequirementLink`, `GoalSatisfactionLink`, and `ProcessTransition` are presented as edges rather than duplicate nodes.              |
| Reference edges     | Examples include `SUPPORTS`, `CONSTRAINS`, `TRIGGERS`, `USES`, `MANAGES`, `ROOT`, and `MEMBER`.                                                                                   |
| Contained details   | Process steps, decision rules, quality scenarios, acceptance criteria, glossary terms, lifecycle states, and readiness checks use contained rows or drill-down views.             |
| Complexity views    | Overview, goals/requirements, capability map, domain model, business process, actor map, aggregate lifecycle, event storming, policy/decision, governance/risk, and traceability. |

The CIM canvas intentionally uses warm, lightweight notation because CIM models are collaborative
business artifacts. Meaning is carried by shape, token, label, and edge semantics, not color alone.

## PIM Syntax

PIM communicates provider-independent serverless architecture. Its visual syntax emphasizes service
boundaries, contracts, flows, policies, and deployable responsibilities.

| Role               | Main choices and examples                                                                                                                                                                          |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Containers         | Services, deployment units, APIs, workflows, event channels, and other containment-owning concepts open into scoped semantic views.                                                                |
| Architecture nodes | Functions, APIs, routes, queues, topics, event buses, stores, adapters, identities, secrets, policies, workflows, and environments are canvas cards.                                               |
| Semantic edges     | Relationship classes and references become invocation, trigger, routing, flow, data-access, permission, transition, deployment, and policy edges.                                                  |
| Reference edges    | Examples include `INVOKES`, `TRIGGERS`, `ROUTES_TO`, `READS`, `WRITES`, `PUBLISHES`, `SUBSCRIBES_TO`, `AUTHORIZED_BY`, and `DEPLOYS_TO`.                                                           |
| Contained details  | Contract fields, schemas, event envelopes, configuration entries, permissions, subscriptions, workflow branches, error handlers, and policy settings use rows, tables, trees, or inspector panels. |
| Readiness concepts | `ImplementationProfile`, `PlatformCapability`, and `PlatformMappingAssessment` are available in the trace/readiness viewpoint.                                                                     |
| Complexity views   | Overview, service landscape, API/contracts, functions/triggers, events/flows, data/storage, policy/observability, workflow, security, configuration/secrets, and trace/readiness.                  |

PIM nodes are responsibility-oriented rather than vendor-oriented. A queue or function is shown by
its architectural role; provider-specific fields and logos are deliberately deferred to PSM.

## AWS PSM Syntax

PSM communicates deployable AWS structure. The visual language emphasizes resources, stages,
stacks, integrations, IAM, operations, and generated infrastructure relationships.

| Role                  | Main choices and examples                                                                                                                                                                                                             |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Containers            | Any resource owning meaningful contained configuration can be opened semantically; stages, stacks, APIs, event buses, IAM resources, and state machines are primary examples.                                                         |
| Resource nodes        | Lambda, API Gateway, EventBridge, SQS, SNS, DynamoDB, S3, Cognito, VPC, CloudWatch, and related deployable resources use AWS-resource cards.                                                                                          |
| Semantic edges        | Dedicated relationship/view classes and references represent routes, invocation, targets, permissions, networking, observation, logging, reads/writes, roles, secrets, and transitions.                                               |
| Reference edges       | Examples include `ROUTES_TO`, `INVOKES`, `TARGETS`, `PERMISSION`, `NETWORKS_WITH`, `OBSERVES`, `USES_ROLE`, and `USES_SECRET`.                                                                                                        |
| Contained details     | IAM documents/statements/principals/conditions, secret-generation settings, SNS filters, metric transformations, value expressions, native properties, parameters, mappings, and ASL/OpenAPI structures use structured detail syntax. |
| Integration shortcuts | Common AWS integrations are displayed as concise semantic edges while preserving their underlying deployable resource objects.                                                                                                        |
| Complexity views      | Overview, stage/stack, API Gateway, Lambda, event/messaging, Step Functions, storage, IAM/secrets, Cognito, networking, observability, CloudFormation/SAM, trace/readiness, and integration shortcuts.                                |

PSM deliberately keeps deeply nested CloudFormation-style configuration out of the main topology.
Such objects remain fully editable through contained tables, trees, and structured panels.

## Complexity Management

All three levels use the same complexity-management principles:

- Named synchronized viewpoints prevent an all-elements canvas.
- Openable containers provide semantic zoom into owned elements.
- Geometric zoom controls card detail and edge-label visibility using per-level `canvasPolicy`.
- Focus mode shows depth-1 or depth-2 neighborhoods.
- Containment, structural, behavioral, policy, trace, and generated relationships are separated by
  viewpoint; individual relationships can also be hidden or restored from the Relations inspector.
- Large structured objects use inspector tables and trees instead of canvas nodes.
- Saved viewpoints preserve scope, filters, layout, and camera position.

## Coverage Guarantee

`ModelingConfigService` derives attributes, references, multiplicities, enums, inheritance,
containments, and legal relationship rules from each combined Ecore metamodel. UI metadata adds the
intentional visual role, notation, viewpoint, and presentation choices.

The modeling configuration tests verify that:

- every Ecore EClass receives complete visual metadata and a `visualRole`;
- all attributes and references are exposed through the generated configuration;
- containments and relationship rules are derived from Ecore;
- every user-creatable top-level type belongs to at least one viewpoint;
- abstract/support and contained-detail types are not incorrectly exposed as ordinary palette nodes.

In the frontend, the palette intentionally contains only standalone `node` and `container` roles.
Containers carry an **Open** badge. Relationship objects are created from legal connection handles,
and `detail` concepts are created and edited in their owner's containment inspector.

When adding a metamodel concept, explicitly decide whether it is a `node`, `container`,
`relationship`, `detail`, or `support` concept and place every top-level creatable concept in at
least one viewpoint.
