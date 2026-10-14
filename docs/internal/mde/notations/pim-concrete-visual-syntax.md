# PIM concrete visual syntax

This document records the concrete syntax decisions for the Platform Independent
Model (PIM) DSML. The executable configuration is
[`methodology/modeling-framework/notation/pim.cvs.json`](../methodology/modeling-framework/notation/pim.cvs.json); this document explains
why its views, palettes, containers, notation, and relationship rules are shaped
the way they are.

The design is based on the combined PIM Ecore model generated from the Emfatic
sources in [`methodology/modeling-framework/metamodels/pim`](../methodology/modeling-framework/metamodels/pim), especially the root
model, deployment, API, compute, contracts, data, integration, workflow, policy,
security, configuration, and external-integration packages. It changes notation
only; it does not change the PIM abstract syntax or semantic validation rules.

## 1. What PIM means

PIM is the provider-independent architecture language for a serverless or hybrid
serverless system. It describes what the system must do and how its parts
collaborate, without committing those parts to a concrete cloud provider.

The language has five important boundaries:

1. **The model workspace.** `PIMModel` is the root and owns the global catalogs:
   services, deployment units and environments, schemas and event types, policies
   and decision models, flows and data-access relationships, identities and
   principals, configuration and secrets, traceability, and readiness.
2. **The service boundary.** `ServerlessService` owns the deployable building
   blocks of a service: functions, APIs, event channels, schedules, stores,
   workflows, and external adapters. This is the primary user-facing container.
3. **The execution boundary.** Functions, triggers, API routes, workflow steps,
   schedules, channels, and flows express execution and event movement. These are
   topology and behavior, so important connections are drawn as edges.
4. **The data and contract boundary.** Schemas, fields, API contracts, function
   contracts, event types, data models, access patterns, and data-access records
   describe shapes and usage. Trees, tables, and inspector chips are more useful
   than putting every field and binding on the architecture canvas.
5. **The assurance boundary.** Policies, security, configuration, trace links,
   platform mapping assessments, and production readiness explain constraints and
   fitness. These are separate views and overlays so operational detail does not
   overwhelm the runtime architecture.

The result is a language in which a user can start with services, open a service
to work on its contents, and then open a schema, store, workflow, policy, or
configuration set for deeper editing. Cross-boundary references remain navigable
links rather than being mistaken for containment.

## 2. Visual grammar

The element definitions and visual rules use a small, reusable visual vocabulary:

| Concept                      | Visual treatment                                  | Intended meaning                                                               |
| ---------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------ |
| Service or workflow boundary | Large container card                              | A named scope that owns model elements.                                        |
| API                          | API card                                          | A public or internal surface with routes, contracts, and policies.             |
| Function                     | Function card                                     | A compute unit with execution, data, event, and policy ports.                  |
| Schema and contract detail   | Schema tree/table                                 | A nested structure whose fields are edited as data, not as architecture nodes. |
| Store and data model         | Data card                                         | A persistence boundary or logical data structure.                              |
| Event/channel                | Event card                                        | A message/event concept or transport boundary.                                 |
| Workflow step                | Process node, diamond, ellipse, or double ellipse | Task, decision, start, and terminal semantics.                                 |
| Relationship                 | Labeled directed edge                             | A meaningful runtime, ownership, governance, deployment, or trace relation.    |
| Reference                    | Compact chip in a card/table/inspector            | A binding or high-cardinality reference that should not create canvas noise.   |

The package rules provide stable colors and categories. More specific rules give
services, APIs, functions, schemas, events, stores, workflows, workflow steps,
external integrations, schedules, and mapping assessments domain-specific tags and
summary fields. The type token is not replaced by color, so the notation remains
usable in dark mode, grayscale, and dense diagrams.

Kernel objects such as expressions, cardinalities, annotations, and traceability
metadata are intentionally inspector/detail concepts. `TraceModel` is available
as a root-level overlay, while `TraceLink` is rendered as a distinct dashed trace
edge.

## 3. Views and global palettes

Every view has an explicit `palette` for creation and `canvas` for display.
Concern-specific canvases can also show contained consumers through `scopeTypes`:
API routes and contracts, functions and their contracts, and storage details.
These elements remain owned by their service, API, function, or store; their
creation palettes remain inside those owners.

| View                                                        | Global palette and canvas                                                                                                 | Main question                                                                                | Edge layers                                                                                                                                                |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PIM Overview** (`pim-architecture-overview`)              | `ServerlessService`, `ExternalEndpoint`, `IdentityProvider`, `BusinessRule`, `DecisionModel`, `Secret`, `Environment`     | What are the major service, external, governance, identity, and environment concepts?        | Ownership/deployment, API and execution, data/event movement, flows, policy attachment, mapping, trace.                                                    |
| **Service Landscape** (`pim-service-landscape`)             | `ServerlessService`, `DeploymentUnit`, `Environment`                                                                      | Which services are deployed together and to which environments?                              | `OWNS`, `DEPLOYS`, `DEPLOYS_TO`, `CONTAINS`, membership variants.                                                                                          |
| **API Surface** (`pim-api-surface`)                         | Palette: `ServerlessService`, `Schema`; canvas also includes `Api`, `ApiRoute`, `ApiContract`, `ErrorMapping`             | Which APIs and routes use each request, response, or error schema?                           | API execution, authorization, and schema bindings.                                                                                                         |
| **Contract Catalog** (`pim-contract-catalog`)               | `Schema`, `EventType`                                                                                                     | What are the reusable message and data contracts?                                            | Event schema bindings and selected contract attachment links. Fields remain schema-local detail.                                                           |
| **Function and Trigger** (`pim-function-trigger`)           | Palette: `ServerlessService`, `ExternalEndpoint`, `Schema`; canvas also includes `Function`, `FunctionContract`           | How do functions execute and which input, output, and error schemas do they use?             | Execution, data access, events, external calls, and function schema bindings.                                                                              |
| **Event and Flow** (`pim-event-integration`)                | `ServerlessService`, `EventType`, `ExternalEndpoint`, `Schema`                                                            | How do events and flows connect the architecture and which payload schemas do events use?    | Event and message movement, invocation, and event schema bindings.                                                                                         |
| **Data and Storage** (`pim-data-design`)                    | Palette: `ServerlessService`; canvas also includes `DataStore`, `ObjectStore`, `DataModel`, `AccessPattern`               | Which stores and data models persist data and which access patterns use them?                | `READS`, `WRITES`, `APPEND`, `DELETE`, `DATA_ACCESS`, `USED_BY_FUNCTIONS`, `EXPOSED_BY_ROUTES`, `ATTACHED_TO`.                                             |
| **Policy and Observability** (`pim-policy-observability`)   | Concrete root policies, business rules, decision models, resilience policies, and observability configurations            | What constraints, decisions, resilience controls, and operational signals govern the design? | `ATTACHED_TO`, `CONSTRAINS`, `USED_BY_FUNCTIONS`, `EXPOSED_BY_ROUTES`, `AUTHORIZED_BY`. Nested policy settings open inside their owning policy.            |
| **Workflow** (`pim-workflow-designer`)                      | Scoped `Workflow` palette/canvas                                                                                          | What is the process sequence, branching, error handling, and orchestration behavior?         | `TRANSITION`, `INVOKES`, `ORCHESTRATES`, `EXTERNAL_CALL`, `TRIGGERS`. The workflow scope permits a contained workflow to be selected as the entry surface. |
| **Security** (`pim-security-policy`)                        | `IdentityProvider`, `Principal`, security policies, `Secret`                                                              | Who can access what, through which identity and authorization policy?                        | `PERMISSION`, `AUTHORIZED_BY`, `USES_SECRET`, `ATTACHED_TO`, `CONSTRAINS`. Permissions are drawn edges, not loose palette nodes.                           |
| **Configuration and Secrets** (`pim-configuration-secrets`) | `ConfigurationSet`, `Secret`, `Environment`                                                                               | Which configuration sets and secrets apply to which environments and configurable elements?  | `HAS_ENV`, `USES_SECRET`, `ATTACHED_TO`, `DEPLOYS_TO`. Parameters and variables are edited inside a configuration set.                                     |
| **Trace and Readiness** (`pim-readiness-traceability`)      | `TraceModel`, `ProductionReadinessAssessment`, `ImplementationProfile`, `PlatformCapability`, `PlatformMappingAssessment` | Is the PIM model traceable, implementable, and ready for its target platform?                | `TRACE`, `MAPS_TO`, `ATTACHED_TO`, `CONSTRAINS`. Findings/checks/decisions stay on the readiness board and in the inspector.                               |

The API Surface canvas includes APIs, routes, contracts, error mappings, and
schemas, with request, response, and error schema edges. Function and Trigger
includes functions, function contracts, and schemas, with input, output, and
error schema edges. Event and Flow and Contract Catalog show event types with
their schemas. Contract Catalog remains the central collection of reusable
schemas. Data and Storage displays services, data stores, object stores, data
models, and access patterns; schema creation belongs to the contract views.

The overview is deliberately not a projection of every PIM EClass. Service-owned
elements appear in the service landscape and concern-specific views through their
service container. This avoids the common failure mode where a useful architecture
view becomes an unreadable catalog of implementation details.
It is also the default workbench view, so a new model opens on a useful
architectural starting surface rather than an empty workflow editor.

## 4. Containment palettes and open behavior

The following are the explicit container profiles. In each case the `palette` and
`canvas` are intentionally identical: anything offered inside the container can be
seen immediately after it is created or when the container is opened.

### Primary modeling containers

| Opened element        | Palette/canvas                                                                                                               | Why this boundary is real                                                                                                                                                                                                    |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ServerlessService`   | `Function`, `Api`, `Queue`, `Topic`, `EventBus`, `Schedule`, `DataStore`, `ObjectStore`, `Workflow`, `ExternalAdapter`       | These are the service's actual containment references in `pim-deployment.emf`.                                                                                                                                               |
| `Api`                 | `ApiRoute`, `ApiContract`                                                                                                    | Routes and the API contract are owned by the API.                                                                                                                                                                            |
| `ApiRoute`            | `ErrorMapping`                                                                                                               | Route-specific error mappings are route-owned details.                                                                                                                                                                       |
| `Function`            | `FunctionContract`                                                                                                           | The contract is a function-owned nested object; triggers are relationship objects created as edges.                                                                                                                          |
| `Schema`              | `SchemaField`, `SchemaConstraint`                                                                                            | A schema is edited as a tree/table of fields and constraints.                                                                                                                                                                |
| `SchemaField`         | `SchemaEnumLiteral`, `SchemaValidationConstraint`, nested `SchemaField`                                                      | Enum values, field constraints, and array/map subfields are field-owned detail.                                                                                                                                              |
| `DataStore`           | `DataModel`, `AccessPattern`, `IndexCandidate`, `DataChangeStream`                                                           | These describe the logical data design inside a store.                                                                                                                                                                       |
| `DataModel`           | `DataField`                                                                                                                  | Storage fields are owned by the data model.                                                                                                                                                                                  |
| `ConfigurationSet`    | `ConfigParameter`, `EnvironmentVariable`                                                                                     | Configuration values belong to a set; they are not independent architecture nodes.                                                                                                                                           |
| `DecisionModel`       | `DecisionRule`                                                                                                               | Decision rules are a table owned by their decision model.                                                                                                                                                                    |
| `ResiliencePolicy`    | `RetryPolicy`, `DeadLetterPolicy`                                                                                            | Resilience settings are nested policy settings, not standalone governance concepts.                                                                                                                                          |
| `ObservabilityConfig` | `LoggingPolicy`, `MetricPolicy`, `TracingPolicy`, `AlertPolicy`, `Slo`                                                       | The observability bundle owns its signal and SLO settings.                                                                                                                                                                   |
| `MetricPolicy`        | `MetricDimension`                                                                                                            | Dimensions are metric-owned table rows.                                                                                                                                                                                      |
| `Workflow`            | `StartStep`, `SuccessEndStep`, `FailureEndStep`, `TaskStep`, `ChoiceStep`, `ParallelStep`, `MapStep`, `WaitStep`, `PassStep` | Steps form the workflow process graph. `WorkflowTransition` is created by connecting steps.                                                                                                                                  |
| `TaskStep`            | `RetryPolicy`, `ErrorHandler`, `CallbackTaskConfig`                                                                          | These are actual inherited/owned task details. `HumanTask` and `ApprovalTask` are references to root-owned human-task concepts, so they are selected through the inspector rather than incorrectly created as task children. |
| `ParallelStep`        | `ParallelBranch`                                                                                                             | Branches are parallel-step-owned scopes.                                                                                                                                                                                     |
| `ParallelBranch`      | All concrete workflow step types                                                                                             | A branch owns its own step sequence and transitions.                                                                                                                                                                         |
| `MapStep`             | `MapStateConfig`                                                                                                             | The map state configuration is map-step-owned.                                                                                                                                                                               |
| `MapStateConfig`      | `ParallelBranch`                                                                                                             | The item processor is modeled as a branch scope.                                                                                                                                                                             |
| `ExternalAdapter`     | `CredentialRequirement`                                                                                                      | Adapter credentials are owned configuration details.                                                                                                                                                                         |

### Relationship-only containers

`Topic`, `EventBus`, `ObjectStore`, `Principal`, `TraceModel`, and
`ProductionReadinessAssessment` have explicit empty palettes where their
containments are relationship or support objects:

- `Topic` owns `Subscription` objects.
- `EventBus` owns `EventRoutingRule` objects.
- `ObjectStore` owns `ObjectNotificationRule` objects.
- `Principal` owns `Permission` objects.
- `TraceModel` owns `TraceLink` objects.
- `ProductionReadinessAssessment` owns readiness findings, checks, and manual
  decisions.

These are not missing features. The first five are created through connector
actions or relationship editors, and readiness/support objects are managed in
tables, overlays, or inspectors. Exposing them as ordinary draggable nodes would
make users construct meaningless standalone objects and would confuse ownership
with topology.

## 5. Edge versus inspector strategy

The core rule is: draw a relationship when its direction changes the architecture
or execution story; use the inspector when the reference is a binding, option set,
inverse index, or high-cardinality detail.

### Relationships rendered as edges

| Group                       | PIM concepts and fields                                                                                                                          | Reason                                                                                                                                               |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Service/deployment topology | `ServiceElementMembership`; deployment-unit `services`, `contains`, and `targetEnvironments`                                                     | Ownership, reuse, deployment, and environment placement are architecture-level facts.                                                                |
| API execution               | `ApiRoute.functionIntegration`, `ApiRoute.workflowIntegration`, API authentication links                                                         | A route's target and authorization path are meaningful on an API diagram.                                                                            |
| Function execution          | `Trigger`; task/choice/error-handler invocation references; `Function.callsAdapters`; schedule `targets`                                         | These are executable control paths.                                                                                                                  |
| Event movement              | Function publish/subscribe references; flow subclasses; subscriptions; event-routing and object-notification rules                               | Producers, consumers, channels, and event paths are the natural content of an event-flow view.                                                       |
| Data movement               | `DataAccess` with its `READ`, `WRITE`, `READ_WRITE`, `APPEND`, and `DELETE` modes; function read/write references; access-pattern usage/exposure | Data movement is important enough to trace, while the operation mode remains on the edge inspector.                                                  |
| Security and governance     | `Permission`, policy `attachedTo`, API/auth-policy links, allowed principals, business-rule enforcement                                          | These links explain who may act and which constraints govern a resource. They use separate edge layers so they can be hidden in a runtime-only view. |
| Readiness and traceability  | `PlatformMappingAssessment.source`, `TraceLink`                                                                                                  | Mapping and trace are explicit cross-model evidence, not containment. They use distinct view and edge styling.                                       |

Relationship objects remain first-class in the serialized model, but their visual
role is `relationship`. The user edits their mode, effect, condition, ownership
kind, flow purpose, or confidence by selecting the edge. This preserves the full
abstract syntax without forcing relationship records onto the node palette.

### References kept in the inspector or as chips

Nested schema-field bindings, object metadata schema collections,
policy settings (`timeout`, `resilience`, `observability`,
rate limits, CORS, and security-policy collections), configuration internals,
event/channel indexes, inverse producer/consumer collections, and expression or
metadata objects stay in the inspector. They are still navigable and editable;
they simply do not create a line for every implementation binding.

Schema bindings remain editable in the inspector and appear as labeled edges in
the API, function, event, and contract views. These views expose the consumers
alongside the reusable schema nodes. The Data and Storage view keeps its focus
on persistence structures; `DataModel.schema` remains available in the inspector.

The configuration also excludes the old invalid semantic kinds
`PERMISSION_TARGET`, `USES_ROUTE`, `USES_ROLE`, and `WRITES_LOGS_TO`. Those strings
were not declared in the PIM `relationshipKinds` vocabulary and therefore could
not produce reliable legal connectors. The replacement uses declared kinds such
as `PERMISSION`, `AUTHORIZED_BY`, `ATTACHED_TO`, and `CONSTRAINS`.

## 6. Important corrections from the previous CVS

The updated file addresses the following concrete problems:

- **Schedule is a node/detail, not an edge object.** `Schedule` is contained by a
  service and owns schedule attributes plus `targets`; it now appears in the
  service palette and its target reference is the `TRIGGERS` edge.
- **Platform mapping assessment is a node with a mapping edge.**
  `PlatformMappingAssessment` is root-contained and has a `source` reference. It
  now appears in the readiness view as a mapping-assessment card, with `MAPS_TO`
  from the assessment to the source concept.
- **The wildcard relationship rule was removed.** Ecore-derived and explicit
  semantic rules now determine legal connectors, so unrelated concepts do not
  acquire every possible edge kind.
- **The raw CVS reference mapping section is populated.** It now mirrors the
  intentional semantic reference rules instead of being empty, which keeps the
  admin export and runtime configuration consistent.
- **Relationship-only types are removed from ordinary palettes.** Flow classes,
  permissions, subscriptions, routing rules, notification rules, and transitions
  are created as edges. Human/approval tasks are not incorrectly offered inside a
  `TaskStep`, because they are references to root-owned task concepts.
- **Nested editing is complete for meaningful containment boundaries.** API,
  schema, data, configuration, decision, resilience, observability, workflow
  branching, and external-adapter details now have explicit profiles.
- **Concrete notation is no longer fallback-only.** Package defaults and specific
  type rules provide service, API, function, schema, event, data, workflow,
  external, relationship, schedule, trace, and mapping-assessment notation.

## 7. Complexity management

The configuration uses several complementary controls:

- named concern-specific views instead of one all-purpose diagram;
- service and nested-container focus instead of global display of contained-only
  types;
- table/tree surfaces for contracts, fields, rules, policies, and configuration;
- edge-layer filtering and distinct styling for runtime, data, governance, and
  trace edges;
- semantic zoom: boundaries and counts at low zoom, card fields and ports at
  higher zoom, and labels/details only when useful;
- relationship-only container profiles with empty palettes where an owner has no
  meaningful child nodes;
- badges for lifecycle, production, security, encryption, review, generation, and
  severity signals without turning those signals into extra nodes.

The guiding interaction is therefore:

```text
Global view
  -> choose a service or concern
     -> open the owning container
        -> create contained nodes or draw semantic edges
           -> select a node/edge for full attributes and high-cardinality references
```

This mirrors the PIM model itself: containment is spatial, cross-boundary meaning
is relational, and implementation/detail data is inspectable without dominating
the architecture canvas.

## 8. Validation performed

The changed CVS parses as JSON, retains the required CVS v2 fields, and was
validated through the focused modeling configuration test suite:

```text
mvn -pl packages/java/platform-modeling-infrastructure -Dtest=ModelingConfigServiceTest test
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

No formatter or linter was run, in accordance with the repository instructions.
