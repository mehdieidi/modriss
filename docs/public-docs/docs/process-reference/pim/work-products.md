# PIM modeling: work products

This page documents the **SPEM WorkProductDefinition, WorkProductUse, and ProcessParameter** elements used by the PIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A WorkProductDefinition describes maintained information or a tangible result. WorkProductUse binds that definition to an activity or task as an input or output. ProcessParameter makes the direction explicit. A work product can be stored in several physical files or systems if its identity, owner, revision, and evidence links remain clear.

## Summary

| Work product                | Kind     | Producing tasks | Consuming tasks | Uses |
| --------------------------- | -------- | --------------: | --------------: | ---: |
| PIM Increment Plan          | Artifact |               1 |               7 |    8 |
| Architecture Posture        | Artifact |               3 |              13 |   16 |
| Service Boundary Map        | Artifact |               2 |              20 |   22 |
| Contract Catalog            | Artifact |               2 |              16 |   18 |
| Data Architecture           | Artifact |               3 |               7 |   10 |
| Compute Catalog             | Artifact |               2 |              12 |   14 |
| API Catalog                 | Artifact |               2 |              14 |   16 |
| Integration Topology        | Artifact |               2 |              10 |   12 |
| Workflow Model              | Artifact |               2 |               5 |    7 |
| Security Model              | Artifact |               2 |               7 |    9 |
| Architecture Policy Catalog | Artifact |               6 |               9 |   15 |
| Configuration Package       | Artifact |               2 |               4 |    6 |
| Platform Readiness Record   | Artifact |               2 |               2 |    4 |
| PIM Increment Review Record | Artifact |               1 |               0 |    1 |

## Detailed work products

## PIM Increment Plan

<small>Artifact: `pim-artifact.increment-plan` · 8 WorkProductUse occurrences</small>

Selected service slice, trace baseline, and architecture definition of done

**Produced or updated by**

- Plan service slice

**Consumed by**

- Create PIM model root
- Set architecture posture
- Establish shared model contract and evidence conventions
- Define serverless services
- Define schemas and fields
- Complete trace and readiness
- Review and adapt PIM increment

**Work-product uses**

- `wpu.pim.ph1.st0.t1.output.pim-artifact.increment-plan` as **output** in task `pim.ph1.st0.t1`
- `wpu.pim.ph1.st1.t1.input.pim-artifact.increment-plan` as **input** in task `pim.ph1.st1.t1`
- `wpu.pim.ph1.st1.t2.input.pim-artifact.increment-plan` as **input** in task `pim.ph1.st1.t2`
- `wpu.pim.ph1.st1.t3.input.pim-artifact.increment-plan` as **input** in task `pim.ph1.st1.t3`
- `wpu.pim.ph1.st2.t1.input.pim-artifact.increment-plan` as **input** in task `pim.ph1.st2.t1`
- `wpu.pim.ph2.st1.t1.input.pim-artifact.increment-plan` as **input** in task `pim.ph2.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.increment-plan` as **input** in task `pim.ph6.st2.t1`
- `wpu.pim.ph6.st3.t1.input.pim-artifact.increment-plan` as **input** in task `pim.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Architecture Posture

<small>Artifact: `pim-artifact.architecture-posture` · 16 WorkProductUse occurrences</small>

PIMModel root and implementation profile

**Produced or updated by**

- Create PIM model root
- Set architecture posture
- Establish shared model contract and evidence conventions

**Consumed by**

- Set architecture posture
- Establish shared model contract and evidence conventions
- Define serverless services
- Assign element memberships
- Define schemas and fields
- Model data stores and models
- Configure identity providers and principals
- Define permissions and security policies
- Model external endpoints and adapters
- Configure environments and deployment units
- Assess platform capabilities
- Complete trace and readiness
- Review and adapt PIM increment

**Work-product uses**

- `wpu.pim.ph1.st1.t1.output.pim-artifact.architecture-posture` as **output** in task `pim.ph1.st1.t1`
- `wpu.pim.ph1.st1.t2.input.pim-artifact.architecture-posture` as **input** in task `pim.ph1.st1.t2`
- `wpu.pim.ph1.st1.t2.output.pim-artifact.architecture-posture` as **output** in task `pim.ph1.st1.t2`
- `wpu.pim.ph1.st1.t3.input.pim-artifact.architecture-posture` as **input** in task `pim.ph1.st1.t3`
- `wpu.pim.ph1.st1.t3.output.pim-artifact.architecture-posture` as **output** in task `pim.ph1.st1.t3`
- `wpu.pim.ph1.st2.t1.input.pim-artifact.architecture-posture` as **input** in task `pim.ph1.st2.t1`
- `wpu.pim.ph1.st2.t2.input.pim-artifact.architecture-posture` as **input** in task `pim.ph1.st2.t2`
- `wpu.pim.ph2.st1.t1.input.pim-artifact.architecture-posture` as **input** in task `pim.ph2.st1.t1`
- `wpu.pim.ph2.st2.t1.input.pim-artifact.architecture-posture` as **input** in task `pim.ph2.st2.t1`
- `wpu.pim.ph5.st1.t1.input.pim-artifact.architecture-posture` as **input** in task `pim.ph5.st1.t1`
- `wpu.pim.ph5.st1.t2.input.pim-artifact.architecture-posture` as **input** in task `pim.ph5.st1.t2`
- `wpu.pim.ph5.st3.t1.input.pim-artifact.architecture-posture` as **input** in task `pim.ph5.st3.t1`
- `wpu.pim.ph5.st3.t2.input.pim-artifact.architecture-posture` as **input** in task `pim.ph5.st3.t2`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.architecture-posture` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.architecture-posture` as **input** in task `pim.ph6.st2.t1`
- `wpu.pim.ph6.st3.t1.input.pim-artifact.architecture-posture` as **input** in task `pim.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Service Boundary Map

<small>Artifact: `pim-artifact.service-map` · 22 WorkProductUse occurrences</small>

Serverless services and memberships

**Produced or updated by**

- Define serverless services
- Assign element memberships

**Consumed by**

- Assign element memberships
- Define schemas and fields
- Model event types and envelopes
- Model data stores and models
- Define access patterns and indexes
- Configure change streams and notifications
- Define functions and contracts
- Define APIs and routes
- Model event channels and buses
- Model workflows and states
- Configure human tasks and error handling
- Configure identity providers and principals
- Define permissions and security policies
- Apply resilience policies
- Configure observability policies
- Define alerts, SLOs, and CORS
- Map business rules and decision models
- Model external endpoints and adapters
- Assess platform capabilities
- Complete trace and readiness

**Work-product uses**

- `wpu.pim.ph1.st2.t1.output.pim-artifact.service-map` as **output** in task `pim.ph1.st2.t1`
- `wpu.pim.ph1.st2.t2.input.pim-artifact.service-map` as **input** in task `pim.ph1.st2.t2`
- `wpu.pim.ph1.st2.t2.output.pim-artifact.service-map` as **output** in task `pim.ph1.st2.t2`
- `wpu.pim.ph2.st1.t1.input.pim-artifact.service-map` as **input** in task `pim.ph2.st1.t1`
- `wpu.pim.ph2.st1.t2.input.pim-artifact.service-map` as **input** in task `pim.ph2.st1.t2`
- `wpu.pim.ph2.st2.t1.input.pim-artifact.service-map` as **input** in task `pim.ph2.st2.t1`
- `wpu.pim.ph2.st2.t2.input.pim-artifact.service-map` as **input** in task `pim.ph2.st2.t2`
- `wpu.pim.ph2.st2.t3.input.pim-artifact.service-map` as **input** in task `pim.ph2.st2.t3`
- `wpu.pim.ph3.st1.t1.input.pim-artifact.service-map` as **input** in task `pim.ph3.st1.t1`
- `wpu.pim.ph3.st2.t1.input.pim-artifact.service-map` as **input** in task `pim.ph3.st2.t1`
- `wpu.pim.ph4.st1.t1.input.pim-artifact.service-map` as **input** in task `pim.ph4.st1.t1`
- `wpu.pim.ph4.st2.t1.input.pim-artifact.service-map` as **input** in task `pim.ph4.st2.t1`
- `wpu.pim.ph4.st2.t2.input.pim-artifact.service-map` as **input** in task `pim.ph4.st2.t2`
- `wpu.pim.ph5.st1.t1.input.pim-artifact.service-map` as **input** in task `pim.ph5.st1.t1`
- `wpu.pim.ph5.st1.t2.input.pim-artifact.service-map` as **input** in task `pim.ph5.st1.t2`
- `wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.service-map` as **input** in task `pim.ph5.st2.ss1.t1`
- `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.service-map` as **input** in task `pim.ph5.st2.ss2.t1`
- `wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.service-map` as **input** in task `pim.ph5.st2.ss2.t2`
- `wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.service-map` as **input** in task `pim.ph5.st2.ss3.t2`
- `wpu.pim.ph5.st3.t1.input.pim-artifact.service-map` as **input** in task `pim.ph5.st3.t1`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.service-map` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.service-map` as **input** in task `pim.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Contract Catalog

<small>Artifact: `pim-artifact.contract-catalog` · 18 WorkProductUse occurrences</small>

Schemas, event types, envelopes

**Produced or updated by**

- Define schemas and fields
- Model event types and envelopes

**Consumed by**

- Model event types and envelopes
- Model data stores and models
- Define access patterns and indexes
- Configure change streams and notifications
- Define functions and contracts
- Configure triggers and runtime
- Define APIs and routes
- Map API contracts and errors
- Model event channels and buses
- Define flows and routing rules
- Model workflows and states
- Configure human tasks and error handling
- Configure identity providers and principals
- Define permissions and security policies
- Model external endpoints and adapters
- Complete trace and readiness

**Work-product uses**

- `wpu.pim.ph2.st1.t1.output.pim-artifact.contract-catalog` as **output** in task `pim.ph2.st1.t1`
- `wpu.pim.ph2.st1.t2.input.pim-artifact.contract-catalog` as **input** in task `pim.ph2.st1.t2`
- `wpu.pim.ph2.st1.t2.output.pim-artifact.contract-catalog` as **output** in task `pim.ph2.st1.t2`
- `wpu.pim.ph2.st2.t1.input.pim-artifact.contract-catalog` as **input** in task `pim.ph2.st2.t1`
- `wpu.pim.ph2.st2.t2.input.pim-artifact.contract-catalog` as **input** in task `pim.ph2.st2.t2`
- `wpu.pim.ph2.st2.t3.input.pim-artifact.contract-catalog` as **input** in task `pim.ph2.st2.t3`
- `wpu.pim.ph3.st1.t1.input.pim-artifact.contract-catalog` as **input** in task `pim.ph3.st1.t1`
- `wpu.pim.ph3.st1.t2.input.pim-artifact.contract-catalog` as **input** in task `pim.ph3.st1.t2`
- `wpu.pim.ph3.st2.t1.input.pim-artifact.contract-catalog` as **input** in task `pim.ph3.st2.t1`
- `wpu.pim.ph3.st2.t2.input.pim-artifact.contract-catalog` as **input** in task `pim.ph3.st2.t2`
- `wpu.pim.ph4.st1.t1.input.pim-artifact.contract-catalog` as **input** in task `pim.ph4.st1.t1`
- `wpu.pim.ph4.st1.t2.input.pim-artifact.contract-catalog` as **input** in task `pim.ph4.st1.t2`
- `wpu.pim.ph4.st2.t1.input.pim-artifact.contract-catalog` as **input** in task `pim.ph4.st2.t1`
- `wpu.pim.ph4.st2.t2.input.pim-artifact.contract-catalog` as **input** in task `pim.ph4.st2.t2`
- `wpu.pim.ph5.st1.t1.input.pim-artifact.contract-catalog` as **input** in task `pim.ph5.st1.t1`
- `wpu.pim.ph5.st1.t2.input.pim-artifact.contract-catalog` as **input** in task `pim.ph5.st1.t2`
- `wpu.pim.ph5.st3.t1.input.pim-artifact.contract-catalog` as **input** in task `pim.ph5.st3.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.contract-catalog` as **input** in task `pim.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Data Architecture

<small>Artifact: `pim-artifact.data-architecture` · 10 WorkProductUse occurrences</small>

Stores, models, access patterns

**Produced or updated by**

- Model data stores and models
- Define access patterns and indexes
- Configure change streams and notifications

**Consumed by**

- Define access patterns and indexes
- Configure change streams and notifications
- Define functions and contracts
- Configure throughput and ordering policies
- Apply governance and compliance policies
- Assess platform capabilities
- Complete trace and readiness

**Work-product uses**

- `wpu.pim.ph2.st2.t1.output.pim-artifact.data-architecture` as **output** in task `pim.ph2.st2.t1`
- `wpu.pim.ph2.st2.t2.input.pim-artifact.data-architecture` as **input** in task `pim.ph2.st2.t2`
- `wpu.pim.ph2.st2.t2.output.pim-artifact.data-architecture` as **output** in task `pim.ph2.st2.t2`
- `wpu.pim.ph2.st2.t3.input.pim-artifact.data-architecture` as **input** in task `pim.ph2.st2.t3`
- `wpu.pim.ph2.st2.t3.output.pim-artifact.data-architecture` as **output** in task `pim.ph2.st2.t3`
- `wpu.pim.ph3.st1.t1.input.pim-artifact.data-architecture` as **input** in task `pim.ph3.st1.t1`
- `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.data-architecture` as **input** in task `pim.ph5.st2.ss1.t2`
- `wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.data-architecture` as **input** in task `pim.ph5.st2.ss3.t1`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.data-architecture` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.data-architecture` as **input** in task `pim.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Compute Catalog

<small>Artifact: `pim-artifact.compute-catalog` · 14 WorkProductUse occurrences</small>

Functions, contracts, triggers

**Produced or updated by**

- Define functions and contracts
- Configure triggers and runtime

**Consumed by**

- Configure triggers and runtime
- Define APIs and routes
- Map API contracts and errors
- Model event channels and buses
- Define flows and routing rules
- Define permissions and security policies
- Apply resilience policies
- Configure throughput and ordering policies
- Configure observability policies
- Configure environments and deployment units
- Assess platform capabilities
- Complete trace and readiness

**Work-product uses**

- `wpu.pim.ph3.st1.t1.output.pim-artifact.compute-catalog` as **output** in task `pim.ph3.st1.t1`
- `wpu.pim.ph3.st1.t2.input.pim-artifact.compute-catalog` as **input** in task `pim.ph3.st1.t2`
- `wpu.pim.ph3.st1.t2.output.pim-artifact.compute-catalog` as **output** in task `pim.ph3.st1.t2`
- `wpu.pim.ph3.st2.t1.input.pim-artifact.compute-catalog` as **input** in task `pim.ph3.st2.t1`
- `wpu.pim.ph3.st2.t2.input.pim-artifact.compute-catalog` as **input** in task `pim.ph3.st2.t2`
- `wpu.pim.ph4.st1.t1.input.pim-artifact.compute-catalog` as **input** in task `pim.ph4.st1.t1`
- `wpu.pim.ph4.st1.t2.input.pim-artifact.compute-catalog` as **input** in task `pim.ph4.st1.t2`
- `wpu.pim.ph5.st1.t2.input.pim-artifact.compute-catalog` as **input** in task `pim.ph5.st1.t2`
- `wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.compute-catalog` as **input** in task `pim.ph5.st2.ss1.t1`
- `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.compute-catalog` as **input** in task `pim.ph5.st2.ss1.t2`
- `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.compute-catalog` as **input** in task `pim.ph5.st2.ss2.t1`
- `wpu.pim.ph5.st3.t2.input.pim-artifact.compute-catalog` as **input** in task `pim.ph5.st3.t2`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.compute-catalog` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.compute-catalog` as **input** in task `pim.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## API Catalog

<small>Artifact: `pim-artifact.api-catalog` · 16 WorkProductUse occurrences</small>

APIs, routes, error mappings

**Produced or updated by**

- Define APIs and routes
- Map API contracts and errors

**Consumed by**

- Map API contracts and errors
- Model event channels and buses
- Define flows and routing rules
- Model workflows and states
- Configure human tasks and error handling
- Define permissions and security policies
- Configure throughput and ordering policies
- Configure observability policies
- Define alerts, SLOs, and CORS
- Map business rules and decision models
- Model external endpoints and adapters
- Configure environments and deployment units
- Assess platform capabilities
- Complete trace and readiness

**Work-product uses**

- `wpu.pim.ph3.st2.t1.output.pim-artifact.api-catalog` as **output** in task `pim.ph3.st2.t1`
- `wpu.pim.ph3.st2.t2.input.pim-artifact.api-catalog` as **input** in task `pim.ph3.st2.t2`
- `wpu.pim.ph3.st2.t2.output.pim-artifact.api-catalog` as **output** in task `pim.ph3.st2.t2`
- `wpu.pim.ph4.st1.t1.input.pim-artifact.api-catalog` as **input** in task `pim.ph4.st1.t1`
- `wpu.pim.ph4.st1.t2.input.pim-artifact.api-catalog` as **input** in task `pim.ph4.st1.t2`
- `wpu.pim.ph4.st2.t1.input.pim-artifact.api-catalog` as **input** in task `pim.ph4.st2.t1`
- `wpu.pim.ph4.st2.t2.input.pim-artifact.api-catalog` as **input** in task `pim.ph4.st2.t2`
- `wpu.pim.ph5.st1.t2.input.pim-artifact.api-catalog` as **input** in task `pim.ph5.st1.t2`
- `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.api-catalog` as **input** in task `pim.ph5.st2.ss1.t2`
- `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.api-catalog` as **input** in task `pim.ph5.st2.ss2.t1`
- `wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.api-catalog` as **input** in task `pim.ph5.st2.ss2.t2`
- `wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.api-catalog` as **input** in task `pim.ph5.st2.ss3.t2`
- `wpu.pim.ph5.st3.t1.input.pim-artifact.api-catalog` as **input** in task `pim.ph5.st3.t1`
- `wpu.pim.ph5.st3.t2.input.pim-artifact.api-catalog` as **input** in task `pim.ph5.st3.t2`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.api-catalog` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.api-catalog` as **input** in task `pim.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Integration Topology

<small>Artifact: `pim-artifact.integration-topology` · 12 WorkProductUse occurrences</small>

Channels, flows, schedules

**Produced or updated by**

- Model event channels and buses
- Define flows and routing rules

**Consumed by**

- Define flows and routing rules
- Model workflows and states
- Configure human tasks and error handling
- Apply resilience policies
- Configure throughput and ordering policies
- Configure observability policies
- Model external endpoints and adapters
- Configure environments and deployment units
- Assess platform capabilities
- Complete trace and readiness

**Work-product uses**

- `wpu.pim.ph4.st1.t1.output.pim-artifact.integration-topology` as **output** in task `pim.ph4.st1.t1`
- `wpu.pim.ph4.st1.t2.input.pim-artifact.integration-topology` as **input** in task `pim.ph4.st1.t2`
- `wpu.pim.ph4.st1.t2.output.pim-artifact.integration-topology` as **output** in task `pim.ph4.st1.t2`
- `wpu.pim.ph4.st2.t1.input.pim-artifact.integration-topology` as **input** in task `pim.ph4.st2.t1`
- `wpu.pim.ph4.st2.t2.input.pim-artifact.integration-topology` as **input** in task `pim.ph4.st2.t2`
- `wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.integration-topology` as **input** in task `pim.ph5.st2.ss1.t1`
- `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.integration-topology` as **input** in task `pim.ph5.st2.ss1.t2`
- `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.integration-topology` as **input** in task `pim.ph5.st2.ss2.t1`
- `wpu.pim.ph5.st3.t1.input.pim-artifact.integration-topology` as **input** in task `pim.ph5.st3.t1`
- `wpu.pim.ph5.st3.t2.input.pim-artifact.integration-topology` as **input** in task `pim.ph5.st3.t2`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.integration-topology` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.integration-topology` as **input** in task `pim.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Workflow Model

<small>Artifact: `pim-artifact.workflow-model` · 7 WorkProductUse occurrences</small>

Workflows, human tasks, escalation

**Produced or updated by**

- Model workflows and states
- Configure human tasks and error handling

**Consumed by**

- Configure human tasks and error handling
- Configure observability policies
- Define alerts, SLOs, and CORS
- Map business rules and decision models
- Complete trace and readiness

**Work-product uses**

- `wpu.pim.ph4.st2.t1.output.pim-artifact.workflow-model` as **output** in task `pim.ph4.st2.t1`
- `wpu.pim.ph4.st2.t2.input.pim-artifact.workflow-model` as **input** in task `pim.ph4.st2.t2`
- `wpu.pim.ph4.st2.t2.output.pim-artifact.workflow-model` as **output** in task `pim.ph4.st2.t2`
- `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.workflow-model` as **input** in task `pim.ph5.st2.ss2.t1`
- `wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.workflow-model` as **input** in task `pim.ph5.st2.ss2.t2`
- `wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.workflow-model` as **input** in task `pim.ph5.st2.ss3.t2`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.workflow-model` as **input** in task `pim.ph6.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Security Model

<small>Artifact: `pim-artifact.security-model` · 9 WorkProductUse occurrences</small>

Identity, principals, permissions

**Produced or updated by**

- Configure identity providers and principals
- Define permissions and security policies

**Consumed by**

- Define permissions and security policies
- Apply resilience policies
- Apply governance and compliance policies
- Configure environments and deployment units
- Assess platform capabilities
- Complete trace and readiness
- Review and adapt PIM increment

**Work-product uses**

- `wpu.pim.ph5.st1.t1.output.pim-artifact.security-model` as **output** in task `pim.ph5.st1.t1`
- `wpu.pim.ph5.st1.t2.input.pim-artifact.security-model` as **input** in task `pim.ph5.st1.t2`
- `wpu.pim.ph5.st1.t2.output.pim-artifact.security-model` as **output** in task `pim.ph5.st1.t2`
- `wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.security-model` as **input** in task `pim.ph5.st2.ss1.t1`
- `wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.security-model` as **input** in task `pim.ph5.st2.ss3.t1`
- `wpu.pim.ph5.st3.t2.input.pim-artifact.security-model` as **input** in task `pim.ph5.st3.t2`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.security-model` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.security-model` as **input** in task `pim.ph6.st2.t1`
- `wpu.pim.ph6.st3.t1.input.pim-artifact.security-model` as **input** in task `pim.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Architecture Policy Catalog

<small>Artifact: `pim-artifact.policy-catalog` · 15 WorkProductUse occurrences</small>

Operational and compliance policies

**Produced or updated by**

- Apply resilience policies
- Configure throughput and ordering policies
- Configure observability policies
- Define alerts, SLOs, and CORS
- Apply governance and compliance policies
- Map business rules and decision models

**Consumed by**

- Configure throughput and ordering policies
- Configure observability policies
- Define alerts, SLOs, and CORS
- Apply governance and compliance policies
- Map business rules and decision models
- Model external endpoints and adapters
- Assess platform capabilities
- Complete trace and readiness
- Review and adapt PIM increment

**Work-product uses**

- `wpu.pim.ph5.st2.ss1.t1.output.pim-artifact.policy-catalog` as **output** in task `pim.ph5.st2.ss1.t1`
- `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.policy-catalog` as **input** in task `pim.ph5.st2.ss1.t2`
- `wpu.pim.ph5.st2.ss1.t2.output.pim-artifact.policy-catalog` as **output** in task `pim.ph5.st2.ss1.t2`
- `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.policy-catalog` as **input** in task `pim.ph5.st2.ss2.t1`
- `wpu.pim.ph5.st2.ss2.t1.output.pim-artifact.policy-catalog` as **output** in task `pim.ph5.st2.ss2.t1`
- `wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.policy-catalog` as **input** in task `pim.ph5.st2.ss2.t2`
- `wpu.pim.ph5.st2.ss2.t2.output.pim-artifact.policy-catalog` as **output** in task `pim.ph5.st2.ss2.t2`
- `wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.policy-catalog` as **input** in task `pim.ph5.st2.ss3.t1`
- `wpu.pim.ph5.st2.ss3.t1.output.pim-artifact.policy-catalog` as **output** in task `pim.ph5.st2.ss3.t1`
- `wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.policy-catalog` as **input** in task `pim.ph5.st2.ss3.t2`
- `wpu.pim.ph5.st2.ss3.t2.output.pim-artifact.policy-catalog` as **output** in task `pim.ph5.st2.ss3.t2`
- `wpu.pim.ph5.st3.t1.input.pim-artifact.policy-catalog` as **input** in task `pim.ph5.st3.t1`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.policy-catalog` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.policy-catalog` as **input** in task `pim.ph6.st2.t1`
- `wpu.pim.ph6.st3.t1.input.pim-artifact.policy-catalog` as **input** in task `pim.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Configuration Package

<small>Artifact: `pim-artifact.config-package` · 6 WorkProductUse occurrences</small>

Environments, secrets, external adapters

**Produced or updated by**

- Model external endpoints and adapters
- Configure environments and deployment units

**Consumed by**

- Configure environments and deployment units
- Assess platform capabilities
- Complete trace and readiness
- Review and adapt PIM increment

**Work-product uses**

- `wpu.pim.ph5.st3.t1.output.pim-artifact.config-package` as **output** in task `pim.ph5.st3.t1`
- `wpu.pim.ph5.st3.t2.input.pim-artifact.config-package` as **input** in task `pim.ph5.st3.t2`
- `wpu.pim.ph5.st3.t2.output.pim-artifact.config-package` as **output** in task `pim.ph5.st3.t2`
- `wpu.pim.ph6.st1.t1.input.pim-artifact.config-package` as **input** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.config-package` as **input** in task `pim.ph6.st2.t1`
- `wpu.pim.ph6.st3.t1.input.pim-artifact.config-package` as **input** in task `pim.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Platform Readiness Record

<small>Artifact: `pim-artifact.platform-readiness` · 4 WorkProductUse occurrences</small>

Mapping assessment, trace, readiness

**Produced or updated by**

- Assess platform capabilities
- Complete trace and readiness

**Consumed by**

- Complete trace and readiness
- Review and adapt PIM increment

**Work-product uses**

- `wpu.pim.ph6.st1.t1.output.pim-artifact.platform-readiness` as **output** in task `pim.ph6.st1.t1`
- `wpu.pim.ph6.st2.t1.input.pim-artifact.platform-readiness` as **input** in task `pim.ph6.st2.t1`
- `wpu.pim.ph6.st2.t1.output.pim-artifact.platform-readiness` as **output** in task `pim.ph6.st2.t1`
- `wpu.pim.ph6.st3.t1.input.pim-artifact.platform-readiness` as **input** in task `pim.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## PIM Increment Review Record

<small>Artifact: `pim-artifact.increment-review` · 1 WorkProductUse occurrence</small>

Architecture review outcomes, accepted service slice, and improvement actions

**Produced or updated by**

- Review and adapt PIM increment

**Consumed by**

- No task in this scope declares this item as an input.

**Work-product uses**

- `wpu.pim.ph6.st3.t1.output.pim-artifact.increment-review` as **output** in task `pim.ph6.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

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
