# PSM concrete visual syntax and notation

## Scope and design goal

This document records the PSM concrete visual syntax decisions implemented in
[`mde/notation/psm.cvs.json`](../mde/notation/psm.cvs.json). The goal is to make AWS
platform models understandable at three different scales:

1. an account and deployment architecture,
2. a service topology made of deployable AWS resources, and
3. the detailed configuration of one resource.

The configuration is deliberately driven by the PSM Ecore containment structure. A
palette entry is therefore a modeling action with a predictable meaning: it either
creates a standalone/deployable resource in the current view or creates a child of
the container that is currently open. Configuration values that do not improve a
topology diagram stay in the attributes/details pane.

The analysis used all PSM Emfatic sources under `mde/metamodels/psm`, the shared
kernel metamodel, and the generated `psm-combined.ecore`. The runtime merge in
`ModelingConfigService` remains authoritative for structural fields, containment
legality, abstract-type expansion, and contained-only inference.

## What the PSM models

PSM is an AWS deployment and infrastructure model, not a single application-flow
diagram. Its main concepts form the following hierarchy.

| Conceptual layer                     | Main metamodel concepts                                                                                                             | Visual treatment                                                                                                                                            |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Account/workspace                    | `AwsPsmModel`                                                                                                                       | One workspace container with account, partition, region, naming, tagging, and production posture.                                                           |
| Deployment                           | `AwsStage`, `SamStack`                                                                                                              | Containers. Stages deploy stacks; stacks own CloudFormation/SAM parameters and the resource membership boundary.                                            |
| Deployable resource                  | `AwsResource` and its concrete subclasses                                                                                           | Resource cards. These are the units users reason about in architecture and deployment views.                                                                |
| Service topology                     | API Gateway, Lambda, EventBridge, SQS/SNS, DynamoDB/S3, IAM/KMS/Secrets, Cognito, VPC, CloudWatch, and Step Functions               | Separate focused views with service-appropriate layouts and edge vocabularies.                                                                              |
| Resource configuration               | `NativeProperty`, `ValueExpression`, tags, imports, policies, indexes, request/response models, retry policies, and similar classes | Nested detail palettes where composition is meaningful, plus structured attributes/details forms.                                                           |
| Workflow                             | `StepFunctionStateMachine` → `AslDocument` → `AslState`                                                                             | A process view. States are nodes and `nextState` references are transition edges. Branches, retry rules, catches, and map configuration are nested details. |
| Governance and traceability          | `TraceModel`, `TraceLink`, `ProductionReadinessAssessment`, readiness findings/checks, and structured documents                     | Separate overlay/view. They should not add noise to the deployment topology.                                                                                |
| Generated integration representation | `AwsRelationshipView` and its eight concrete integration views                                                                      | Edge objects, never normal palette nodes. They provide readable shortcuts over several underlying AWS resources.                                            |

This separation is important because many PSM classes are intentionally not
resources. For example, an `EventBridgeTarget`, `IamStatement`, `DynamoDbGlobalSecondaryIndex`,
or `AslRetryRule` describes part of another object. Showing those classes beside
stacks and Lambda functions would make the main diagram look like an implementation
dump rather than an architecture model.

## View strategy

The default workbench view is `psm-portfolio-governance`. It gives users a useful
starting point without forcing them to choose a service before they can see the
model. The other views are focused working contexts. Every CVS view keeps its raw
`palette` and `canvas` lists identical; the backend still removes abstract,
support-only, relationship, and contained-only entries when a metamodel merge says
that they cannot be created in that context.

| View                           | Palette intention                                                                                                                                                    | Main edge vocabulary                                                                                           | Layout     |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ---------- |
| `psm-portfolio-governance`     | Account, stages, stacks, governance policies, and primary AWS resources across services.                                                                             | Deployment, dependency, invocation, event/message flow, target, role, encryption, observation, logging, trace. | Container  |
| `psm-resource-topology`        | Only `SamStack` and `AwsStage`; useful for deployment boundaries and blast-radius reasoning.                                                                         | `DEPLOYS`, `DEPENDS_ON`, `TRACE`.                                                                              | Container  |
| `psm-api-edge`                 | HTTP, REST, and WebSocket APIs plus API-level deployable companions such as domains, keys, usage plans, integrations, deployments, validators, and WAF associations. | Routing, invocation, authorization, logging, role use, dependency.                                             | Table      |
| `psm-lambda-compute`           | Lambda functions, reusable layer versions, and code-signing configuration.                                                                                           | Invocation, event flow, role use, logging, dependency.                                                         | Layered    |
| `psm-messaging`                | EventBridge buses/rules/schedules/pipes/connections/destinations and SQS/SNS resources and policies/subscriptions.                                                   | Event flow, message flow, target, invocation, role use, dependency.                                            | Event flow |
| `psm-workflow-asl`             | Entry palette is only `StepFunctionStateMachine`; opening it leads to its ASL document and states.                                                                   | State transition, invocation, observation, role use.                                                           | Process    |
| `psm-data-persistence`         | DynamoDB tables, S3 buckets, and bucket policies. Indexes and bucket configuration are opened from their owners.                                                     | Reads, writes, event flow, encryption, dependency.                                                             | Table      |
| `psm-security-access`          | IAM roles/policies, KMS keys/aliases, Secrets Manager, SSM, and resource policies.                                                                                   | Permission, authorization, role use, secret use, encryption, attachment, dependency.                           | Governance |
| `psm-cognito-identity`         | User pools, clients, groups, domains, and identity pools.                                                                                                            | Authorization, permission, role use, invocation, dependency.                                                   | Governance |
| `psm-networking`               | VPCs, VPC endpoints, and security groups. Subnets and security-group rules are configured through the stack/VPC context rather than crowding this palette.           | Network relationship, attachment, dependency.                                                                  | Container  |
| `psm-observability-operations` | Log groups, metric filters, log subscriptions, alarms, composite alarms, and dashboards.                                                                             | Observation, log destination, action target, role use, encryption, dependency.                                 | Table      |
| `psm-configuration-cfn`        | `SamStack` and `AwsNativeResource` for parameters, mappings, conditions, outputs, globals, metadata, and native CloudFormation resources.                            | Deployment and trace.                                                                                          | Table      |
| `psm-traceability-readiness`   | `TraceModel` as the entry point to trace links and governance overlays.                                                                                              | Trace, constraint, attachment.                                                                                 | Matrix     |
| `psm-runtime-flows`            | APIs, functions, state machines, event sources, queues/topics, and buckets for end-to-end runtime flow analysis.                                                     | Routes, invokes, event/message flow, targets, reads, writes, observation.                                      | Event flow |

The previous relationship-only integration view was removed as a user-facing
palette. Relationship-view EClasses are not useful objects to create manually in a
normal diagram: they are generated or maintained representations of an existing
multi-object integration. The eight shortcut connector rules remain in the CVS so
the frontend can render those integrations as clean edges.

## Containment and “open” behavior

The container profiles are aligned with actual `val` containments. The runtime also
derives a legal fallback palette for every Ecore owner, while the CVS supplies
curated profiles for the high-value navigation boundaries below.

### Account and deployment boundaries

- `AwsPsmModel` opens to stages, stacks, global SAM settings, naming/tagging/security
  policies, trace data, and structured documents.
- `AwsStage` exposes stage tags. Deployment account, region, approval, and
  environment data stay as stage attributes because they are not independent
  architectural nodes.
- `SamStack` exposes CloudFormation parameters, mappings, conditions, outputs,
  native resources, and the primary deployable resource families. The palette does
  not expose API routes, API stages, authorizers, Lambda permissions/versions/
  aliases, or event targets as peer resources. Those belong to their service
  owner’s focused context.

The stack is still the membership boundary in the metamodel. This means a resource
can be deployed by a stack without being drawn as a child implementation node in
every stack diagram.

### API Gateway

The API profiles are type-specific:

- `HttpApi` opens to `HttpApiRoute` and `HttpApiStage`.
- `RestApi` opens to `RestApiRoute`, `RestApiResource`, `RestApiMethod`, and
  `RestApiStage`.
- `WebSocketApi` opens to `WebSocketRoute` and `WebSocketStage`.
- Each API can open to the shared CORS/tracing/access-log/policy detail objects and
  the three authorizer variants.
- A route opens to request models, response models, and route settings. Its
  `integration` and `authorizer` references are represented through the inspector
  and semantic edges, rather than by putting an integration object inside the
  route’s containment palette.

This prevents an HTTP route from accidentally being created as a REST or WebSocket
route and avoids the previous cross-protocol palette leak.

### Lambda

Opening `AwsLambdaFunction` exposes the executable code alternatives, environment
variables, dead-letter/tracing/logging/filesystem configuration, SAM events, VPC
attachment, permissions, versions, aliases, event-invoke configuration, event
source mappings, and function URL. The function card itself shows only stable
identity and runtime facts; detailed code and policy values belong in the details
pane or focused child canvas.

Event-source mappings remain nested under Lambda. Their queue/table/resource
references become event-flow edges or remain inspector references, depending on the
semantic mapping, so users do not have to duplicate a source resource merely to
configure a trigger.

### Events and messaging

- An `EventBridgeRule` opens to its event pattern and `EventBridgeTarget` objects.
- A target opens to target parameters, expressions, retry policy, and input
  transformation details. Its target resource and role are edges.
- A schedule opens to its flexible window and target.
- A connection opens to the correct API-key/basic/OAuth authorization detail and
  HTTP parameters.
- SQS queues open to redrive and redrive-allow policies; SNS subscriptions open to
  filter rules; policy resources open to IAM policy documents.

The rule/target hierarchy is therefore visible when editing a rule, while a
messaging topology still shows the queue, topic, function, and destination as the
important graph nodes.

### Data, security, identity, networking, and operations

- DynamoDB tables open to key definitions, indexes, throughput, replicas, streams,
  TTL, SSE, backups, and resource policy details. Indexes then open to their key
  schema/projection/throughput details.
- S3 buckets open to ownership, CORS, encryption, lifecycle, public-access block,
  notifications, and replication. Notification and replication rules remain
  nested; their destinations and roles are semantic references.
- IAM roles open to assume-role policy and inline policies; policy documents open
  to statements; statements open to principals and conditions. KMS keys open to
  their policy; secrets and parameters open to value/generation/rotation details.
- Cognito pools open to recovery, email, Lambda trigger, password, and schema
  details. Clients open to OAuth configuration.
- VPC attachment details keep VPC, subnet, and security-group references in the
  inspector with `ATTACHED_TO`/`NETWORKS_WITH` edges. VPCs do not contain subnets in
  the PSM Ecore, so the CVS does not invent a VPC containment relationship.
- Log groups are resource nodes; metric filters, subscriptions, alarms, and
  dashboards are operational resources. Metric dimensions and transformations are
  nested details, not graph nodes in the normal operations view.

### Step Functions / ASL

The workflow navigation is intentionally two levels deep:

`StepFunctionStateMachine` → `AslDocument` → ASL states.

The state machine palette does not expose states directly. An ASL document exposes
the eight concrete state types. Every state can open retry/catch/choice rules,
branches, and map configuration. `nextState` references become `TRANSITION` edges;
`invokedResource` references become `INVOKES` edges. This gives users a process
diagram without losing the ASL-specific configuration model.

## Edge versus attributes-pane policy

An edge is justified when it answers a cross-resource question a user would ask of
an architecture diagram. A value stays in the attributes pane when drawing it
would add a node that is merely a serialization/configuration fragment.

| Relationship or information                                                 | Treatment                                          | Reason                                                                                       |
| --------------------------------------------------------------------------- | -------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Stage → stack deployment                                                    | `DEPLOYS` edge                                     | Deployment topology and environment impact are architectural.                                |
| Resource `dependsOn`                                                        | `DEPENDS_ON` edge                                  | Explicit CloudFormation dependency affects ordering and blast radius.                        |
| API route → Lambda/Step Functions integration                               | `INVOKES`/generated shortcut edge                  | Runtime behavior is more useful than an integration configuration box.                       |
| EventBridge rule/target → target resource                                   | `TARGETS` edge                                     | Shows the event destination without duplicating target configuration.                        |
| S3/SNS/SQS/Lambda event relationships                                       | `EVENT_FLOW` or `MESSAGE_FLOW` edge                | These are runtime paths users need to trace.                                                 |
| Resource → role / policy / authorizer                                       | `USES_ROLE`, `PERMISSION`, or `AUTHORIZED_BY` edge | Access control and trust boundaries are cross-resource concerns.                             |
| Resource → KMS key                                                          | `ENCRYPTED_BY` edge                                | Encryption ownership is a security architecture concern and is easy to audit visually.       |
| VPC, subnet, endpoint, security-group references                            | `ATTACHED_TO` or `NETWORKS_WITH` edge              | Network placement and connectivity are topology.                                             |
| Log/metric source → log group, destination, monitored resource              | `WRITES_LOGS_TO`/`OBSERVES` edge                   | Operational dependencies must be visible during operations analysis.                         |
| ASL `nextState`                                                             | `TRANSITION` edge                                  | It is the workflow graph itself.                                                             |
| Trace links                                                                 | `TRACE` overlay edge                               | Traceability is valuable but should be independently filterable.                             |
| `NativeProperty`, `ValueExpression`, tags, imports, retry/input details     | Attributes or nested detail                        | These are configuration values, not architecture nodes.                                      |
| IAM statements, principals, conditions, policy JSON                         | Nested policy canvas/details                       | Security needs structure, but individual statements should not pollute the service topology. |
| API request/response models and route settings                              | Nested API/route details                           | They configure a route and have no independent deployment topology.                          |
| DynamoDB indexes, S3 lifecycle rules, notification rules, metric dimensions | Nested resource details                            | They are important to edit but are not independently useful at the overview scale.           |
| `AwsRelationshipView` objects                                               | Generated edge layer                               | They collapse several implementation objects into one readable relationship.                 |

References that are not in the explicit semantic mapping are deliberately retained
as inspector fields. This is safer than turning every Ecore reference into an edge:
the PSM has many back-references and association bookkeeping links whose visual
rendering would produce duplicate arrows or cycles.

## Notation and visual hierarchy

The CVS adds a consistent notation contract on top of the Ecore-derived fields:

- account, stage, stack, API, workflow, resource, security, data, network,
  messaging, operations, trace, and generated-edge tags distinguish visual roles;
- container shapes are used for account, stage, stack, APIs, Lambda functions, ASL
  documents, and trace workspaces;
- resource cards show stable identity first (`logicalId`, name, resource type,
  physical name) and service identity second;
- service-specific cards show real PSM attributes such as `functionName`,
  `memorySizeMb`, `tableName`, `bucketName`, `queueName`, `topicName`,
  `parameterName`, `logGroupName`, and `stateMachineName`;
- generated relationship objects use an edge notation and never appear as
  draggable resource cards;
- stale UI-only concepts and impossible field lists were removed from the PSM
  element entries. The backend now supplies structural attributes and references
  from the Ecore model, preventing a field from being shown merely because it was
  copied from another DSML.

The canvas policy uses larger resource cards than detail objects, container-focused
navigation, semantic zoom, and delayed edge labels for dense diagrams. Far zoom is
therefore useful for architecture, normal zoom for service topology, and near zoom
for configuration inspection.

## Why this balances usability and complexity

The resulting syntax follows one interaction rule throughout the PSM:

> Create a deployable thing in a service view; open it to configure the things it
> owns; connect it to other deployable things only when the connection communicates
> runtime, deployment, security, data, network, operations, workflow, or trace
> semantics.

This gives users a small vocabulary at each level without hiding any metamodel
capability. Every contained Ecore feature still has a derived containment palette,
and every reference/attribute remains available through structural metadata and the
attributes pane. The focused views reduce the number of competing concepts, while
the runtime-flow view provides a single place to follow a request or event across
service boundaries.

The design also keeps generated shortcuts reversible. A clean edge can represent an
API-to-Lambda integration or S3-to-queue notification, but the underlying
integration, permission, notification, policy, subscription, or mapping remains
editable in its owning resource. Users get readable diagrams without sacrificing
CloudFormation/SAM fidelity.

## Verification expectations

The PSM CVS is structurally validated against the combined Ecore metamodel by the
existing configuration service. The focused platform-modeling tests verify:

- palette/canvas parity,
- complete Ecore-derived visual metadata,
- contained-only inference,
- legal service/container palettes,
- semantic reference coverage,
- connectable generated relationship objects, and
- the retained shortcut connector rules.

Semantic EVL validation is intentionally outside the concrete syntax configuration;
this file defines visual affordances and structural modeling navigation, not a
second semantic validator.
