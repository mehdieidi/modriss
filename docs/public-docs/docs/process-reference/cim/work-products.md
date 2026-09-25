# CIM modeling: work products

This page documents the **SPEM WorkProductDefinition, WorkProductUse, and ProcessParameter** elements used by the CIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A WorkProductDefinition describes maintained information or a tangible result. WorkProductUse binds that definition to an activity or task as an input or output. ProcessParameter makes the direction explicit. A work product can be stored in several physical files or systems if its identity, owner, revision, and evidence links remain clear.

## Summary

| Work product                  | Kind     | Producing tasks | Consuming tasks | Uses |
| ----------------------------- | -------- | --------------: | --------------: | ---: |
| CIM Model Root                | Artifact |               2 |               2 |    4 |
| CIM Increment Plan            | Artifact |               1 |               4 |    5 |
| Strategic Intent Package      | Artifact |               2 |              12 |   14 |
| Participation Model           | Artifact |               2 |               6 |    8 |
| Capability                    | Artifact |               2 |               6 |    8 |
| Ubiquitous Language Glossary  | Artifact |               1 |               3 |    4 |
| Information Taxonomy          | Artifact |               2 |               5 |    7 |
| Domain Structure Model        | Artifact |               2 |              11 |   13 |
| CQRS Behavior Surface         | Artifact |               3 |               8 |   11 |
| Aggregate Boundary Model      | Artifact |               1 |               1 |    2 |
| Business Process Model        | Artifact |               1 |               3 |    4 |
| Decision & Policy Model       | Artifact |               1 |               4 |    5 |
| Bounded Context Map           | Artifact |               1 |               3 |    4 |
| Requirements Package          | Artifact |               1 |               4 |    5 |
| Governance Constraint Package | Artifact |               1 |               3 |    4 |
| Transformation Contract       | Artifact |               1 |               1 |    2 |
| Trace & Readiness Record      | Artifact |               1 |               1 |    2 |
| CIM Increment Review Record   | Artifact |               1 |               0 |    1 |

## Detailed work products

## CIM Model Root

<small>Artifact: `cim-artifact.model-root` · 4 WorkProductUse occurrences</small>

CIMModel container and program metadata

**Produced or updated by**

- Create CIM model root
- Establish shared model contract and evidence conventions

**Consumed by**

- Establish shared model contract and evidence conventions
- Plan capability slice

**Work-product uses**

- `wpu.cim.ph1.st1.t1.output.cim-artifact.model-root` as **output** in task `cim.ph1.st1.t1`
- `wpu.cim.ph1.st1.t2.input.cim-artifact.model-root` as **input** in task `cim.ph1.st1.t2`
- `wpu.cim.ph1.st1.t2.output.cim-artifact.model-root` as **output** in task `cim.ph1.st1.t2`
- `wpu.cim.ph1.st2.t1.input.cim-artifact.model-root` as **input** in task `cim.ph1.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## CIM Increment Plan

<small>Artifact: `cim-artifact.increment-plan` · 5 WorkProductUse occurrences</small>

Selected capability slice, working agreements, and definition of done

**Produced or updated by**

- Plan capability slice

**Consumed by**

- Define business goals and KPIs
- Identify stakeholders
- Model actors and roles
- Review and adapt CIM increment

**Work-product uses**

- `wpu.cim.ph1.st2.t1.output.cim-artifact.increment-plan` as **output** in task `cim.ph1.st2.t1`
- `wpu.cim.ph1.st3.t1.input.cim-artifact.increment-plan` as **input** in task `cim.ph1.st3.t1`
- `wpu.cim.ph1.st3.t2.input.cim-artifact.increment-plan` as **input** in task `cim.ph1.st3.t2`
- `wpu.cim.ph2.st1.t1.input.cim-artifact.increment-plan` as **input** in task `cim.ph2.st1.t1`
- `wpu.cim.ph5.st4.t1.input.cim-artifact.increment-plan` as **input** in task `cim.ph5.st4.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Strategic Intent Package

<small>Artifact: `cim-artifact.strategic-intent` · 14 WorkProductUse occurrences</small>

Goals, KPIs, and stakeholder map

**Produced or updated by**

- Define business goals and KPIs
- Identify stakeholders

**Consumed by**

- Identify stakeholders
- Model actors and roles
- Register external systems
- Map business capabilities
- Record capability dependencies
- Define domain glossary
- Define data classifications
- Model business processes
- Formalize requirements
- Apply governance constraints
- Complete trace and readiness
- Review and adapt CIM increment

**Work-product uses**

- `wpu.cim.ph1.st3.t1.output.cim-artifact.strategic-intent` as **output** in task `cim.ph1.st3.t1`
- `wpu.cim.ph1.st3.t2.input.cim-artifact.strategic-intent` as **input** in task `cim.ph1.st3.t2`
- `wpu.cim.ph1.st3.t2.output.cim-artifact.strategic-intent` as **output** in task `cim.ph1.st3.t2`
- `wpu.cim.ph2.st1.t1.input.cim-artifact.strategic-intent` as **input** in task `cim.ph2.st1.t1`
- `wpu.cim.ph2.st1.t2.input.cim-artifact.strategic-intent` as **input** in task `cim.ph2.st1.t2`
- `wpu.cim.ph2.st2.t1.input.cim-artifact.strategic-intent` as **input** in task `cim.ph2.st2.t1`
- `wpu.cim.ph2.st2.t2.input.cim-artifact.strategic-intent` as **input** in task `cim.ph2.st2.t2`
- `wpu.cim.ph2.st3.t1.input.cim-artifact.strategic-intent` as **input** in task `cim.ph2.st3.t1`
- `wpu.cim.ph3.st1.ss1.t1.input.cim-artifact.strategic-intent` as **input** in task `cim.ph3.st1.ss1.t1`
- `wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.strategic-intent` as **input** in task `cim.ph4.st2.ss1.t1`
- `wpu.cim.ph5.st1.t1.input.cim-artifact.strategic-intent` as **input** in task `cim.ph5.st1.t1`
- `wpu.cim.ph5.st1.t2.input.cim-artifact.strategic-intent` as **input** in task `cim.ph5.st1.t2`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.strategic-intent` as **input** in task `cim.ph5.st3.t1`
- `wpu.cim.ph5.st4.t1.input.cim-artifact.strategic-intent` as **input** in task `cim.ph5.st4.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Participation Model

<small>Artifact: `cim-artifact.participation-model` · 8 WorkProductUse occurrences</small>

Actors, roles, and external systems

**Produced or updated by**

- Model actors and roles
- Register external systems

**Consumed by**

- Register external systems
- Map business capabilities
- Model commands and outcomes
- Model queries
- Model events, errors, and conditions
- Apply governance constraints

**Work-product uses**

- `wpu.cim.ph2.st1.t1.output.cim-artifact.participation-model` as **output** in task `cim.ph2.st1.t1`
- `wpu.cim.ph2.st1.t2.input.cim-artifact.participation-model` as **input** in task `cim.ph2.st1.t2`
- `wpu.cim.ph2.st1.t2.output.cim-artifact.participation-model` as **output** in task `cim.ph2.st1.t2`
- `wpu.cim.ph2.st2.t1.input.cim-artifact.participation-model` as **input** in task `cim.ph2.st2.t1`
- `wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.participation-model` as **input** in task `cim.ph3.st3.ss1.t1`
- `wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.participation-model` as **input** in task `cim.ph3.st3.ss2.t1`
- `wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.participation-model` as **input** in task `cim.ph3.st3.ss3.t1`
- `wpu.cim.ph5.st1.t2.input.cim-artifact.participation-model` as **input** in task `cim.ph5.st1.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Capability

<small>Artifact: `cim-artifact.capability-map` · 8 WorkProductUse occurrences</small>

Business capabilities and dependencies

**Produced or updated by**

- Map business capabilities
- Record capability dependencies

**Consumed by**

- Record capability dependencies
- Define domain glossary
- Model domain entities
- Model commands and outcomes
- Define aggregate candidates
- Synthesize bounded contexts

**Work-product uses**

- `wpu.cim.ph2.st2.t1.output.cim-artifact.capability-map` as **output** in task `cim.ph2.st2.t1`
- `wpu.cim.ph2.st2.t2.input.cim-artifact.capability-map` as **input** in task `cim.ph2.st2.t2`
- `wpu.cim.ph2.st2.t2.output.cim-artifact.capability-map` as **output** in task `cim.ph2.st2.t2`
- `wpu.cim.ph2.st3.t1.input.cim-artifact.capability-map` as **input** in task `cim.ph2.st3.t1`
- `wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.capability-map` as **input** in task `cim.ph3.st2.ss1.t1`
- `wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.capability-map` as **input** in task `cim.ph3.st3.ss1.t1`
- `wpu.cim.ph4.st1.t1.input.cim-artifact.capability-map` as **input** in task `cim.ph4.st1.t1`
- `wpu.cim.ph4.st3.t1.input.cim-artifact.capability-map` as **input** in task `cim.ph4.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Ubiquitous Language Glossary

<small>Artifact: `cim-artifact.glossary` · 4 WorkProductUse occurrences</small>

Domain term definitions

**Produced or updated by**

- Define domain glossary

**Consumed by**

- Define data classifications
- Create information items
- Model domain entities

**Work-product uses**

- `wpu.cim.ph2.st3.t1.output.cim-artifact.glossary` as **output** in task `cim.ph2.st3.t1`
- `wpu.cim.ph3.st1.ss1.t1.input.cim-artifact.glossary` as **input** in task `cim.ph3.st1.ss1.t1`
- `wpu.cim.ph3.st1.ss2.t1.input.cim-artifact.glossary` as **input** in task `cim.ph3.st1.ss2.t1`
- `wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.glossary` as **input** in task `cim.ph3.st2.ss1.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Information Taxonomy

<small>Artifact: `cim-artifact.information-taxonomy` · 7 WorkProductUse occurrences</small>

Data classifications and information items

**Produced or updated by**

- Define data classifications
- Create information items

**Consumed by**

- Create information items
- Model domain entities
- Model value objects and relationships
- Model queries
- Apply governance constraints

**Work-product uses**

- `wpu.cim.ph3.st1.ss1.t1.output.cim-artifact.information-taxonomy` as **output** in task `cim.ph3.st1.ss1.t1`
- `wpu.cim.ph3.st1.ss2.t1.input.cim-artifact.information-taxonomy` as **input** in task `cim.ph3.st1.ss2.t1`
- `wpu.cim.ph3.st1.ss2.t1.output.cim-artifact.information-taxonomy` as **output** in task `cim.ph3.st1.ss2.t1`
- `wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.information-taxonomy` as **input** in task `cim.ph3.st2.ss1.t1`
- `wpu.cim.ph3.st2.ss2.t1.input.cim-artifact.information-taxonomy` as **input** in task `cim.ph3.st2.ss2.t1`
- `wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.information-taxonomy` as **input** in task `cim.ph3.st3.ss2.t1`
- `wpu.cim.ph5.st1.t2.input.cim-artifact.information-taxonomy` as **input** in task `cim.ph5.st1.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Domain Structure Model

<small>Artifact: `cim-artifact.domain-structure` · 13 WorkProductUse occurrences</small>

Entities, value objects, relationships, invariants

**Produced or updated by**

- Model domain entities
- Model value objects and relationships

**Consumed by**

- Model value objects and relationships
- Model commands and outcomes
- Model events, errors, and conditions
- Define aggregate candidates
- Model business processes
- Define policies and decision tables
- Synthesize bounded contexts
- Formalize requirements
- Apply governance constraints
- Record transformation metadata
- Complete trace and readiness

**Work-product uses**

- `wpu.cim.ph3.st2.ss1.t1.output.cim-artifact.domain-structure` as **output** in task `cim.ph3.st2.ss1.t1`
- `wpu.cim.ph3.st2.ss2.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph3.st2.ss2.t1`
- `wpu.cim.ph3.st2.ss2.t1.output.cim-artifact.domain-structure` as **output** in task `cim.ph3.st2.ss2.t1`
- `wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph3.st3.ss1.t1`
- `wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph3.st3.ss3.t1`
- `wpu.cim.ph4.st1.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph4.st1.t1`
- `wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph4.st2.ss1.t1`
- `wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph4.st2.ss2.t1`
- `wpu.cim.ph4.st3.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph4.st3.t1`
- `wpu.cim.ph5.st1.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph5.st1.t1`
- `wpu.cim.ph5.st1.t2.input.cim-artifact.domain-structure` as **input** in task `cim.ph5.st1.t2`
- `wpu.cim.ph5.st2.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph5.st2.t1`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.domain-structure` as **input** in task `cim.ph5.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## CQRS Behavior Surface

<small>Artifact: `cim-artifact.behavior-surface` · 11 WorkProductUse occurrences</small>

Commands, queries, events, conditions

**Produced or updated by**

- Model commands and outcomes
- Model queries
- Model events, errors, and conditions

**Consumed by**

- Model queries
- Model events, errors, and conditions
- Define aggregate candidates
- Model business processes
- Define policies and decision tables
- Synthesize bounded contexts
- Formalize requirements
- Complete trace and readiness

**Work-product uses**

- `wpu.cim.ph3.st3.ss1.t1.output.cim-artifact.behavior-surface` as **output** in task `cim.ph3.st3.ss1.t1`
- `wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.behavior-surface` as **input** in task `cim.ph3.st3.ss2.t1`
- `wpu.cim.ph3.st3.ss2.t1.output.cim-artifact.behavior-surface` as **output** in task `cim.ph3.st3.ss2.t1`
- `wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.behavior-surface` as **input** in task `cim.ph3.st3.ss3.t1`
- `wpu.cim.ph3.st3.ss3.t1.output.cim-artifact.behavior-surface` as **output** in task `cim.ph3.st3.ss3.t1`
- `wpu.cim.ph4.st1.t1.input.cim-artifact.behavior-surface` as **input** in task `cim.ph4.st1.t1`
- `wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.behavior-surface` as **input** in task `cim.ph4.st2.ss1.t1`
- `wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.behavior-surface` as **input** in task `cim.ph4.st2.ss2.t1`
- `wpu.cim.ph4.st3.t1.input.cim-artifact.behavior-surface` as **input** in task `cim.ph4.st3.t1`
- `wpu.cim.ph5.st1.t1.input.cim-artifact.behavior-surface` as **input** in task `cim.ph5.st1.t1`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.behavior-surface` as **input** in task `cim.ph5.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Aggregate Boundary Model

<small>Artifact: `cim-artifact.aggregate-model` · 2 WorkProductUse occurrences</small>

Aggregate candidates and consistency rules

**Produced or updated by**

- Define aggregate candidates

**Consumed by**

- Model business processes

**Work-product uses**

- `wpu.cim.ph4.st1.t1.output.cim-artifact.aggregate-model` as **output** in task `cim.ph4.st1.t1`
- `wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.aggregate-model` as **input** in task `cim.ph4.st2.ss1.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Business Process Model

<small>Artifact: `cim-artifact.process-model` · 4 WorkProductUse occurrences</small>

Processes, steps, transitions

**Produced or updated by**

- Model business processes

**Consumed by**

- Define policies and decision tables
- Synthesize bounded contexts
- Complete trace and readiness

**Work-product uses**

- `wpu.cim.ph4.st2.ss1.t1.output.cim-artifact.process-model` as **output** in task `cim.ph4.st2.ss1.t1`
- `wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.process-model` as **input** in task `cim.ph4.st2.ss2.t1`
- `wpu.cim.ph4.st3.t1.input.cim-artifact.process-model` as **input** in task `cim.ph4.st3.t1`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.process-model` as **input** in task `cim.ph5.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Decision & Policy Model

<small>Artifact: `cim-artifact.decision-model` · 5 WorkProductUse occurrences</small>

Policies and decision tables

**Produced or updated by**

- Define policies and decision tables

**Consumed by**

- Synthesize bounded contexts
- Formalize requirements
- Record transformation metadata
- Complete trace and readiness

**Work-product uses**

- `wpu.cim.ph4.st2.ss2.t1.output.cim-artifact.decision-model` as **output** in task `cim.ph4.st2.ss2.t1`
- `wpu.cim.ph4.st3.t1.input.cim-artifact.decision-model` as **input** in task `cim.ph4.st3.t1`
- `wpu.cim.ph5.st1.t1.input.cim-artifact.decision-model` as **input** in task `cim.ph5.st1.t1`
- `wpu.cim.ph5.st2.t1.input.cim-artifact.decision-model` as **input** in task `cim.ph5.st2.t1`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.decision-model` as **input** in task `cim.ph5.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Bounded Context Map

<small>Artifact: `cim-artifact.context-map` · 4 WorkProductUse occurrences</small>

Context boundary assignments

**Produced or updated by**

- Synthesize bounded contexts

**Consumed by**

- Formalize requirements
- Record transformation metadata
- Complete trace and readiness

**Work-product uses**

- `wpu.cim.ph4.st3.t1.output.cim-artifact.context-map` as **output** in task `cim.ph4.st3.t1`
- `wpu.cim.ph5.st1.t1.input.cim-artifact.context-map` as **input** in task `cim.ph5.st1.t1`
- `wpu.cim.ph5.st2.t1.input.cim-artifact.context-map` as **input** in task `cim.ph5.st2.t1`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.context-map` as **input** in task `cim.ph5.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Requirements Package

<small>Artifact: `cim-artifact.requirements-package` · 5 WorkProductUse occurrences</small>

Functional and non-functional requirements

**Produced or updated by**

- Formalize requirements

**Consumed by**

- Apply governance constraints
- Record transformation metadata
- Complete trace and readiness
- Review and adapt CIM increment

**Work-product uses**

- `wpu.cim.ph5.st1.t1.output.cim-artifact.requirements-package` as **output** in task `cim.ph5.st1.t1`
- `wpu.cim.ph5.st1.t2.input.cim-artifact.requirements-package` as **input** in task `cim.ph5.st1.t2`
- `wpu.cim.ph5.st2.t1.input.cim-artifact.requirements-package` as **input** in task `cim.ph5.st2.t1`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.requirements-package` as **input** in task `cim.ph5.st3.t1`
- `wpu.cim.ph5.st4.t1.input.cim-artifact.requirements-package` as **input** in task `cim.ph5.st4.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Governance Constraint Package

<small>Artifact: `cim-artifact.governance-package` · 4 WorkProductUse occurrences</small>

Security, privacy, compliance constraints

**Produced or updated by**

- Apply governance constraints

**Consumed by**

- Record transformation metadata
- Complete trace and readiness
- Review and adapt CIM increment

**Work-product uses**

- `wpu.cim.ph5.st1.t2.output.cim-artifact.governance-package` as **output** in task `cim.ph5.st1.t2`
- `wpu.cim.ph5.st2.t1.input.cim-artifact.governance-package` as **input** in task `cim.ph5.st2.t1`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.governance-package` as **input** in task `cim.ph5.st3.t1`
- `wpu.cim.ph5.st4.t1.input.cim-artifact.governance-package` as **input** in task `cim.ph5.st4.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Transformation Contract

<small>Artifact: `cim-artifact.transformation-contract` · 2 WorkProductUse occurrences</small>

Risks, assumptions, hotspots, profile

**Produced or updated by**

- Record transformation metadata

**Consumed by**

- Complete trace and readiness

**Work-product uses**

- `wpu.cim.ph5.st2.t1.output.cim-artifact.transformation-contract` as **output** in task `cim.ph5.st2.t1`
- `wpu.cim.ph5.st3.t1.input.cim-artifact.transformation-contract` as **input** in task `cim.ph5.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Trace & Readiness Record

<small>Artifact: `cim-artifact.trace-readiness` · 2 WorkProductUse occurrences</small>

TraceModel and production readiness assessment

**Produced or updated by**

- Complete trace and readiness

**Consumed by**

- Review and adapt CIM increment

**Work-product uses**

- `wpu.cim.ph5.st3.t1.output.cim-artifact.trace-readiness` as **output** in task `cim.ph5.st3.t1`
- `wpu.cim.ph5.st4.t1.input.cim-artifact.trace-readiness` as **input** in task `cim.ph5.st4.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## CIM Increment Review Record

<small>Artifact: `cim-artifact.increment-review` · 1 WorkProductUse occurrence</small>

Review outcomes, accepted slice scope, and improvement actions

**Produced or updated by**

- Review and adapt CIM increment

**Consumed by**

- No task in this scope declares this item as an input.

**Work-product uses**

- `wpu.cim.ph5.st4.t1.output.cim-artifact.increment-review` as **output** in task `cim.ph5.st4.t1`

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
