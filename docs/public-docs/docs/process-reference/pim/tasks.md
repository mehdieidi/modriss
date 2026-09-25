# PIM modeling: tasks

This page documents the **SPEM TaskDefinition and TaskUse** elements used by the PIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A TaskDefinition describes reusable work. A TaskUse places that definition inside an activity and binds it to process performers and work-product uses. The same responsibility may appear in another process context with different inputs, outputs, or selected steps.

## Summary

| Task                                                     | Primary role       | Inputs | Outputs | Task uses |
| -------------------------------------------------------- | ------------------ | -----: | ------: | --------: |
| Plan service slice                                       | Solution Architect |      0 |       1 |         1 |
| Create PIM model root                                    | Solution Architect |      1 |       1 |         1 |
| Set architecture posture                                 | Solution Architect |      2 |       1 |         1 |
| Establish shared model contract and evidence conventions | Method Engineer    |      2 |       1 |         1 |
| Define serverless services                               | Solution Architect |      2 |       1 |         1 |
| Assign element memberships                               | Solution Architect |      2 |       1 |         1 |
| Define schemas and fields                                | Solution Architect |      3 |       1 |         1 |
| Model event types and envelopes                          | Solution Architect |      2 |       1 |         1 |
| Model data stores and models                             | Solution Architect |      3 |       1 |         1 |
| Define access patterns and indexes                       | Solution Architect |      3 |       1 |         1 |
| Configure change streams and notifications               | Solution Architect |      3 |       1 |         1 |
| Define functions and contracts                           | Solution Architect |      3 |       1 |         1 |
| Configure triggers and runtime                           | Solution Architect |      2 |       1 |         1 |
| Define APIs and routes                                   | Solution Architect |      3 |       1 |         1 |
| Map API contracts and errors                             | Solution Architect |      3 |       1 |         1 |
| Model event channels and buses                           | Solution Architect |      4 |       1 |         1 |
| Define flows and routing rules                           | Solution Architect |      4 |       1 |         1 |
| Model workflows and states                               | Solution Architect |      4 |       1 |         1 |
| Configure human tasks and error handling                 | Solution Architect |      5 |       1 |         1 |
| Configure identity providers and principals              | Solution Architect |      3 |       1 |         1 |
| Define permissions and security policies                 | Solution Architect |      6 |       1 |         1 |
| Apply resilience policies                                | Solution Architect |      4 |       1 |         1 |
| Configure throughput and ordering policies               | Solution Architect |      5 |       1 |         1 |
| Configure observability policies                         | Solution Architect |      6 |       1 |         1 |
| Define alerts, SLOs, and CORS                            | Solution Architect |      4 |       1 |         1 |
| Apply governance and compliance policies                 | Solution Architect |      3 |       1 |         1 |
| Map business rules and decision models                   | Solution Architect |      4 |       1 |         1 |
| Model external endpoints and adapters                    | Solution Architect |      6 |       1 |         1 |
| Configure environments and deployment units              | Solution Architect |      6 |       1 |         1 |
| Assess platform capabilities                             | Process Reviewer   |      9 |       1 |         1 |
| Complete trace and readiness                             | Process Reviewer   |     13 |       1 |         1 |
| Review and adapt PIM increment                           | Process Reviewer   |      6 |       1 |         1 |

## Detailed tasks

## Plan service slice

<small>Task definition: `task.pim.ph1.st0.t1`</small>

Plan service slice This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- No formal input work product is required. The task still uses the accepted scope, decisions, and project context.

**How to perform the task**

1. Select one service slice traced to CIM goals, bounded contexts, and behavior.
2. Record scope boundaries, architectural risks, manual transform decisions, and definition of done.
3. Confirm the expected PIM EVL gate and review participants for the cycle.

**Outputs**

- PIM Increment Plan

**Ready to start when**

- CIM transform complete, prior PIM increment selected, or greenfield PIM

**Complete when**

- Service-slice scope and definition of done are agreed

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph1.st0.t1` in **Service Slice Planning**

## Create PIM model root

<small>Task definition: `task.pim.ph1.st1.t1`</small>

Create PIM model root This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- PIM Increment Plan

**How to perform the task**

1. Create or verify PIMModel with domain linkage to CIM source.
2. Set modeling date, lifecycle status, and annotation conventions.

**Outputs**

- Architecture Posture

**Ready to start when**

- Service-slice scope agreed

**Complete when**

- PIMModel root exists

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph1.st1.t1` in **Architecture Posture**

## Set architecture posture

<small>Task definition: `task.pim.ph1.st1.t2`</small>

Set architecture posture This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Posture
- PIM Increment Plan

**How to perform the task**

1. Configure ImplementationProfile with serverless posture and platform assumptions.
2. Select ArchitectureStyle and document key architectural decisions.

**Outputs**

- Architecture Posture

**Ready to start when**

- PIM model root exists

**Complete when**

- Architecture style and profile configured

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph1.st1.t2` in **Architecture Posture**

## Establish shared model contract and evidence conventions

<small>Task definition: `task.pim.ph1.st1.t3`</small>

Establish shared model contract and evidence conventions This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Method Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Posture
- PIM Increment Plan

**How to perform the task**

1. Apply shared identity, annotation, traceability, expression, and lifecycle conventions across PIM elements.
2. Record the revision, transformation provenance, and review-state convention for the service slice.
3. Keep support concepts in inspectors and readiness evidence instead of exposing them as false deployable architecture nodes.

**Outputs**

- Architecture Posture

**Ready to start when**

- PIM model root exists

**Complete when**

- Shared model contract and evidence convention are recorded

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph1.st1.t3` in **Architecture Posture**

## Define serverless services

<small>Task definition: `task.pim.ph1.st2.t1`</small>

Define serverless services This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Posture
- PIM Increment Plan

**How to perform the task**

1. Create ServerlessService elements aligned to CIM bounded contexts.
2. Set boundary type and ownership scope per service.

**Outputs**

- Service Boundary Map

**Ready to start when**

- Architecture posture set

**Complete when**

- Services defined for increment slice

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph1.st2.t1` in **Service Boundaries**

## Assign element memberships

<small>Task definition: `task.pim.ph1.st2.t2`</small>

Assign element memberships This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Service Boundary Map
- Architecture Posture

**How to perform the task**

1. Create ServiceElementMembership links from services to planned elements.
2. Set OwnershipKind for each membership.

**Outputs**

- Service Boundary Map

**Ready to start when**

- Services defined

**Complete when**

- Memberships cover deployable boundaries

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph1.st2.t2` in **Service Boundaries**

## Define schemas and fields

<small>Task definition: `task.pim.ph2.st1.t1`</small>

Define schemas and fields This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Service Boundary Map
- Architecture Posture
- PIM Increment Plan

**How to perform the task**

1. Create Schema elements with fields and enum literals.
2. Set schema kind and field types aligned to CIM information items.

**Outputs**

- Contract Catalog

**Ready to start when**

- Service boundaries defined

**Complete when**

- Schemas cover API and message payloads

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph2.st1.t1` in **Contracts & Schemas**

## Model event types and envelopes

<small>Task definition: `task.pim.ph2.st1.t2`</small>

Model event types and envelopes This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Contract Catalog
- Service Boundary Map

**How to perform the task**

1. Define EventType and EventEnvelope elements from CIM events.
2. Apply validation constraints and compatibility rules.

**Outputs**

- Contract Catalog

**Ready to start when**

- Schemas defined

**Complete when**

- Event contracts aligned with CIM behavior surface

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph2.st1.t2` in **Contracts & Schemas**

## Model data stores and models

<small>Task definition: `task.pim.ph2.st2.t1`</small>

Model data stores and models This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Contract Catalog
- Service Boundary Map
- Architecture Posture

**How to perform the task**

1. Create DataStore and ObjectStore elements per service.
2. Define DataModel and DataField structures from CIM entities.

**Outputs**

- Data Architecture

**Ready to start when**

- Contracts defined

**Complete when**

- Stores and models cover domain data

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph2.st2.t1` in **Data Architecture**

## Define access patterns and indexes

<small>Task definition: `task.pim.ph2.st2.t2`</small>

Define access patterns and indexes This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Data Architecture
- Contract Catalog
- Service Boundary Map

**How to perform the task**

1. Model AccessPattern elements from CIM queries and commands.
2. Define IndexCandidate and DataAccess bindings per store.

**Outputs**

- Data Architecture

**Ready to start when**

- Data models drafted

**Complete when**

- Access patterns cover read/write use cases

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph2.st2.t2` in **Data Architecture**

## Configure change streams and notifications

<small>Task definition: `task.pim.ph2.st2.t3`</small>

Configure change streams and notifications This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Data Architecture
- Contract Catalog
- Service Boundary Map

**How to perform the task**

1. Define DataChangeStream elements for event-sourced projections.
2. Configure ObjectNotificationRule for object store triggers.

**Outputs**

- Data Architecture

**Ready to start when**

- Access patterns defined

**Complete when**

- Change streams linked to event contracts

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph2.st2.t3` in **Data Architecture**

## Define functions and contracts

<small>Task definition: `task.pim.ph3.st1.t1`</small>

Define functions and contracts This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Data Architecture
- Service Boundary Map
- Contract Catalog

**How to perform the task**

1. Create Function elements with FunctionContract bindings.
2. Set compute profile and execution model per handler.

**Outputs**

- Compute Catalog

**Ready to start when**

- Data architecture drafted

**Complete when**

- Functions mapped to CIM commands and queries

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph3.st1.t1` in **Compute Units**

## Configure triggers and runtime

<small>Task definition: `task.pim.ph3.st1.t2`</small>

Configure triggers and runtime This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Compute Catalog
- Contract Catalog

**How to perform the task**

1. Define Trigger elements linking functions to events and schedules.
2. Set runtime language and package manager per function.

**Outputs**

- Compute Catalog

**Ready to start when**

- Functions defined

**Complete when**

- Triggers cover behavioral entry points

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph3.st1.t2` in **Compute Units**

## Define APIs and routes

<small>Task definition: `task.pim.ph3.st2.t1`</small>

Define APIs and routes This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Compute Catalog
- Contract Catalog
- Service Boundary Map

**How to perform the task**

1. Create Api elements with routes and HTTP methods.
2. Connect routes to function handlers.

**Outputs**

- API Catalog

**Ready to start when**

- Compute units defined

**Complete when**

- API routes cover public endpoints

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph3.st2.t1` in **API Surface**

## Map API contracts and errors

<small>Task definition: `task.pim.ph3.st2.t2`</small>

Map API contracts and errors This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- API Catalog
- Contract Catalog
- Compute Catalog

**How to perform the task**

1. Bind ApiContract elements to schema definitions.
2. Map ErrorMapping responses to CIM business errors.

**Outputs**

- API Catalog

**Ready to start when**

- API routes defined

**Complete when**

- Contracts and error mappings complete

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph3.st2.t2` in **API Surface**

## Model event channels and buses

<small>Task definition: `task.pim.ph4.st1.t1`</small>

Model event channels and buses This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- API Catalog
- Contract Catalog
- Compute Catalog
- Service Boundary Map

**How to perform the task**

1. Create EventChannel, Queue, Topic, and EventBus elements.
2. Set delivery semantics and ordering requirements.

**Outputs**

- Integration Topology

**Ready to start when**

- API surface defined

**Complete when**

- Channels cover async integration points

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph4.st1.t1` in **Integration Topology**

## Define flows and routing rules

<small>Task definition: `task.pim.ph4.st1.t2`</small>

Define flows and routing rules This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Integration Topology
- API Catalog
- Contract Catalog
- Compute Catalog

**How to perform the task**

1. Model Flow variants connecting producers and consumers.
2. Define EventRoutingRule and Subscription bindings.
3. Configure Schedule triggers for periodic integration.

**Outputs**

- Integration Topology

**Ready to start when**

- Channels defined

**Complete when**

- Flows connect integration endpoints to functions

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph4.st1.t2` in **Integration Topology**

## Model workflows and states

<small>Task definition: `task.pim.ph4.st2.t1`</small>

Model workflows and states This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Integration Topology
- Service Boundary Map
- API Catalog
- Contract Catalog

**How to perform the task**

1. Create Workflow elements from CIM business processes.
2. Define typed workflow steps, transitions, and workflow kind.

**Outputs**

- Workflow Model

**Ready to start when**

- Integration topology defined

**Complete when**

- Workflows cover long-running CIM processes

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph4.st2.t1` in **Workflow Orchestration**

## Configure human tasks and error handling

<small>Task definition: `task.pim.ph4.st2.t2`</small>

Configure human tasks and error handling This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Workflow Model
- Integration Topology
- Service Boundary Map
- API Catalog
- Contract Catalog

**How to perform the task**

1. Add HumanTask and ApprovalTask elements with escalation policies.
2. Define CompensationPolicy and ErrorHandler for failure paths.

**Outputs**

- Workflow Model

**Ready to start when**

- Workflows drafted

**Complete when**

- Human steps and error paths orchestrated

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph4.st2.t2` in **Workflow Orchestration**

## Configure identity providers and principals

<small>Task definition: `task.pim.ph5.st1.t1`</small>

Configure identity providers and principals This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Posture
- Service Boundary Map
- Contract Catalog

**How to perform the task**

1. Define IdentityProvider elements aligned to CIM actors.
2. Register Principal elements for human and service identities.

**Outputs**

- Security Model

**Ready to start when**

- Workflow orchestration drafted

**Complete when**

- Identity model covers all actor types

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st1.t1` in **Security & Identity**

## Define permissions and security policies

<small>Task definition: `task.pim.ph5.st1.t2`</small>

Define permissions and security policies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Security Model
- Architecture Posture
- Service Boundary Map
- API Catalog
- Compute Catalog
- Contract Catalog

**How to perform the task**

1. Create Permission elements with effect and action kind.
2. Apply AuthPolicy and AuthorizationPolicy to APIs and functions.

**Outputs**

- Security Model

**Ready to start when**

- Principals configured

**Complete when**

- Auth model covers all public endpoints

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st1.t2` in **Security & Identity**

## Apply resilience policies

<small>Task definition: `task.pim.ph5.st2.ss1.t1`</small>

Apply resilience policies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Security Model
- Compute Catalog
- Integration Topology
- Service Boundary Map

**How to perform the task**

1. Define ResiliencePolicy on functions and integration flows.
2. Configure RetryPolicy, DeadLetterPolicy, and TimeoutPolicy per target.
3. Set IdempotencyPolicy for state-changing handlers.

**Outputs**

- Architecture Policy Catalog

**Ready to start when**

- Security and identity configured

**Complete when**

- Resilience policies applied to critical paths

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st2.ss1.t1` in **Resilience & Throughput**

## Configure throughput and ordering policies

<small>Task definition: `task.pim.ph5.st2.ss1.t2`</small>

Configure throughput and ordering policies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Policy Catalog
- Data Architecture
- API Catalog
- Compute Catalog
- Integration Topology

**How to perform the task**

1. Set ConcurrencyPolicy and RateLimitPolicy on APIs and functions.
2. Configure BatchPolicy, OrderingPolicy, and CachePolicy where needed.
3. Apply BackupPolicy, RetentionPolicy, and CostPolicy to stores.

**Outputs**

- Architecture Policy Catalog

**Ready to start when**

- Resilience policies applied

**Complete when**

- Throughput and cost policies configured

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st2.ss1.t2` in **Resilience & Throughput**

## Configure observability policies

<small>Task definition: `task.pim.ph5.st2.ss2.t1`</small>

Configure observability policies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Policy Catalog
- Compute Catalog
- API Catalog
- Integration Topology
- Workflow Model
- Service Boundary Map

**How to perform the task**

1. Define ObservabilityConfig per service.
2. Apply LoggingPolicy, MetricPolicy, and TracingPolicy to functions and APIs.

**Outputs**

- Architecture Policy Catalog

**Ready to start when**

- Resilience policies applied

**Complete when**

- Observability baseline configured

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st2.ss2.t1` in **Observability & SLOs**

## Define alerts, SLOs, and CORS

<small>Task definition: `task.pim.ph5.st2.ss2.t2`</small>

Define alerts, SLOs, and CORS This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Policy Catalog
- API Catalog
- Workflow Model
- Service Boundary Map

**How to perform the task**

1. Create AlertPolicy and Slo elements for critical endpoints.
2. Apply CorsPolicy to public API routes.

**Outputs**

- Architecture Policy Catalog

**Ready to start when**

- Observability baseline configured

**Complete when**

- Alerts and SLOs cover critical paths

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st2.ss2.t2` in **Observability & SLOs**

## Apply governance and compliance policies

<small>Task definition: `task.pim.ph5.st2.ss3.t1`</small>

Apply governance and compliance policies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Policy Catalog
- Security Model
- Data Architecture

**How to perform the task**

1. Define ArchitecturePolicy and PolicySetting elements.
2. Apply DataProtectionPolicy and CompliancePolicy from CIM governance.

**Outputs**

- Architecture Policy Catalog

**Ready to start when**

- Observability policies configured

**Complete when**

- Governance policies linked to data and APIs

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st2.ss3.t1` in **Governance & Business Rules**

## Map business rules and decision models

<small>Task definition: `task.pim.ph5.st2.ss3.t2`</small>

Map business rules and decision models This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Policy Catalog
- Workflow Model
- API Catalog
- Service Boundary Map

**How to perform the task**

1. Create BusinessRule elements from CIM policies.
2. Model DecisionModel with DecisionRule rows for branching logic.

**Outputs**

- Architecture Policy Catalog

**Ready to start when**

- Governance policies applied

**Complete when**

- Business rules linked to functions and workflows

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st2.ss3.t2` in **Governance & Business Rules**

## Model external endpoints and adapters

<small>Task definition: `task.pim.ph5.st3.t1`</small>

Model external endpoints and adapters This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Architecture Policy Catalog
- API Catalog
- Integration Topology
- Contract Catalog
- Service Boundary Map
- Architecture Posture

**How to perform the task**

1. Create ExternalEndpoint elements from CIM external systems.
2. Define ExternalAdapter bindings to integration flows.

**Outputs**

- Configuration Package

**Ready to start when**

- Architecture policies applied

**Complete when**

- External integrations modeled

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st3.t1` in **External & Config**

## Configure environments and deployment units

<small>Task definition: `task.pim.ph5.st3.t2`</small>

Configure environments and deployment units This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Configuration Package
- Architecture Posture
- Security Model
- Compute Catalog
- API Catalog
- Integration Topology

**How to perform the task**

1. Define Environment and DeploymentUnit elements per stage.
2. Configure ConfigurationSet, secrets, and credential requirements.
3. Set ConfigParameter and EnvironmentVariable values per environment.

**Outputs**

- Configuration Package

**Ready to start when**

- External integrations modeled

**Complete when**

- Environments and config complete for slice

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph5.st3.t2` in **External & Config**

## Assess platform capabilities

<small>Task definition: `task.pim.ph6.st1.t1`</small>

Assess platform capabilities This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Configuration Package
- Architecture Posture
- Service Boundary Map
- Data Architecture
- Compute Catalog
- API Catalog
- Integration Topology
- Security Model
- Architecture Policy Catalog

**How to perform the task**

1. Review PlatformCapability coverage against modeled elements.
2. Complete PlatformMappingAssessment with gap analysis.

**Outputs**

- Platform Readiness Record

**Ready to start when**

- External and config complete

**Complete when**

- Platform mapping assessment documented

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph6.st1.t1` in **Platform Mapping Assessment**

## Complete trace and readiness

<small>Task definition: `task.pim.ph6.st2.t1`</small>

Complete trace and readiness This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Platform Readiness Record
- PIM Increment Plan
- Architecture Posture
- Service Boundary Map
- Contract Catalog
- Data Architecture
- Compute Catalog
- API Catalog
- Integration Topology
- Workflow Model
- Security Model
- Architecture Policy Catalog
- Configuration Package

**How to perform the task**

1. Build TraceModel links across CIM and PIM elements.
2. Complete ProductionReadinessAssessment and resolve findings.

**Outputs**

- Platform Readiness Record

**Ready to start when**

- Platform mapping assessment complete

**Complete when**

- PIM EVL passes; readiness gate approved

**Checks and evidence**

- pim-semantic-validation

**Uses in this process**

- `pim.ph6.st2.t1` in **Trace & Readiness Gate**

## Review and adapt PIM increment

<small>Task definition: `task.pim.ph6.st3.t1`</small>

Review and adapt PIM increment This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Platform Readiness Record
- PIM Increment Plan
- Architecture Posture
- Security Model
- Architecture Policy Catalog
- Configuration Package

**How to perform the task**

1. Review architecture outcomes against CIM trace links and PIM definition of done.
2. Record accepted scope, deferred architecture decisions, and platform mapping feedback.
3. Create improvement actions and backlog adjustments for the next service slice.

**Outputs**

- PIM Increment Review Record

**Ready to start when**

- PIM EVL passes or all blocking findings are dispositioned

**Complete when**

- Increment accepted or rework loop selected; improvement actions captured

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `pim.ph6.st3.t1` in **Increment Review & Adapt**

## References

These sources explain the standards and practices on which the process structure is based. They are foundations for tailoring and professional judgement, rather than substitutes for project evidence.

- [OMG Software & Systems Process Engineering Meta-Model (SPEM) 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/)
- [ISO/IEC/IEEE 12207:2026, software life cycle processes](https://www.iso.org/standard/90219.html)
- [ISO/IEC/IEEE 15288:2023, system life cycle processes](https://www.iso.org/standard/81702.html)
- [SWEBOK Guide, version 4.0a](https://ieeecs-media.computer.org/media/education/swebok/swebok-v4.pdf)
- [Agile Manifesto principles](https://agilemanifesto.org/principles)
- [The Kanban Guide](https://kanbanguides.org/the-kanban-guide/)
- [FinOps Framework](https://www.finops.org/framework/)
- [AWS Well-Architected Serverless Applications Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
- [Brinkkemper, Method engineering](<https://doi.org/10.1016/S0950-5849(95)01059-9>)
- [Asadi, Esfahani, and Ramsin, Process patterns for MDA-based software development](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf)
