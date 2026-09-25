# CIM modeling: tasks

This page documents the **SPEM TaskDefinition and TaskUse** elements used by the CIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A TaskDefinition describes reusable work. A TaskUse places that definition inside an activity and binds it to process performers and work-product uses. The same responsibility may appear in another process context with different inputs, outputs, or selected steps.

## Summary

| Task                                                     | Primary role          | Inputs | Outputs | Task uses |
| -------------------------------------------------------- | --------------------- | -----: | ------: | --------: |
| Create CIM model root                                    | Business Modeler      |      0 |       1 |         1 |
| Establish shared model contract and evidence conventions | Method Engineer       |      1 |       1 |         1 |
| Plan capability slice                                    | Business Modeler      |      1 |       1 |         1 |
| Define business goals and KPIs                           | Business Modeler      |      1 |       1 |         1 |
| Identify stakeholders                                    | Business Modeler      |      2 |       1 |         1 |
| Model actors and roles                                   | Business Modeler      |      2 |       1 |         1 |
| Register external systems                                | Business Modeler      |      2 |       1 |         1 |
| Map business capabilities                                | Business Modeler      |      2 |       1 |         1 |
| Record capability dependencies                           | Business Modeler      |      2 |       1 |         1 |
| Define domain glossary                                   | Business Modeler      |      2 |       1 |         1 |
| Define data classifications                              | Business Modeler      |      2 |       1 |         1 |
| Create information items                                 | Business Modeler      |      2 |       1 |         1 |
| Model domain entities                                    | Business Modeler      |      3 |       1 |         1 |
| Model value objects and relationships                    | Business Modeler      |      2 |       1 |         1 |
| Model commands and outcomes                              | Business Modeler      |      3 |       1 |         1 |
| Model queries                                            | Business Modeler      |      3 |       1 |         1 |
| Model events, errors, and conditions                     | Business Modeler      |      3 |       1 |         1 |
| Define aggregate candidates                              | Business Modeler      |      3 |       1 |         1 |
| Model business processes                                 | Business Modeler      |      4 |       1 |         1 |
| Define policies and decision tables                      | Business Modeler      |      3 |       1 |         1 |
| Synthesize bounded contexts                              | Business Modeler      |      5 |       1 |         1 |
| Formalize requirements                                   | Requirements Engineer |      5 |       1 |         1 |
| Apply governance constraints                             | Requirements Engineer |      5 |       1 |         1 |
| Record transformation metadata                           | Business Modeler      |      5 |       1 |         1 |
| Complete trace and readiness                             | Process Reviewer      |      9 |       1 |         1 |
| Review and adapt CIM increment                           | Process Reviewer      |      5 |       1 |         1 |

## Detailed tasks

## Create CIM model root

<small>Task definition: `task.cim.ph1.st1.t1`</small>

Create CIM model root This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- No formal input work product is required. The task still uses the accepted scope, decisions, and project context.

**How to perform the task**

1. Create or verify CIMModel with domainName and businessScope.
2. Set organizationName, modelingDate, and language.
3. Apply lifecycle status and annotation conventions on the root.

**Outputs**

- CIM Model Root

**Ready to start when**

- Project created or existing CIM opened for a new increment

**Complete when**

- CIMModel root with domainName exists

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph1.st1.t1` in **Program Charter**

## Establish shared model contract and evidence conventions

<small>Task definition: `task.cim.ph1.st1.t2`</small>

Establish shared model contract and evidence conventions This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Method Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- CIM Model Root

**How to perform the task**

1. Apply the shared identity, annotation, traceability, expression, and lifecycle conventions used by every CIM element.
2. Set the evidence convention for source references, review status, and model-level provenance.
3. Confirm that shared support concepts are handled by inspectors and readiness records rather than mistaken for business concepts.

**Outputs**

- CIM Model Root

**Ready to start when**

- CIM model root exists

**Complete when**

- Shared model contract and evidence convention are recorded

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph1.st1.t2` in **Program Charter**

## Plan capability slice

<small>Task definition: `task.cim.ph1.st2.t1`</small>

Plan capability slice This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- CIM Model Root

**How to perform the task**

1. Select one capability or bounded-context candidate from the modeling backlog.
2. Record slice objective, scope boundaries, assumptions, and definition of done.
3. Identify the validation gate and review participants for the cycle.

**Outputs**

- CIM Increment Plan

**Ready to start when**

- CIM model root exists

**Complete when**

- Capability-slice scope and definition of done are agreed

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph1.st2.t1` in **Capability Slice Planning**

## Define business goals and KPIs

<small>Task definition: `task.cim.ph1.st3.t1`</small>

Define business goals and KPIs This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- CIM Increment Plan

**How to perform the task**

1. Capture BusinessGoal elements with success criteria.
2. Define measurable KPIs linked to each goal.

**Outputs**

- Strategic Intent Package

**Ready to start when**

- Capability-slice scope agreed

**Complete when**

- At least one goal with linked KPI

**Checks and evidence**

- CIM-GOAL-001
- CIM-KPI-001

**Uses in this process**

- `cim.ph1.st3.t1` in **Strategic Framing**

## Identify stakeholders

<small>Task definition: `task.cim.ph1.st3.t2`</small>

Identify stakeholders This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Strategic Intent Package
- CIM Increment Plan

**How to perform the task**

1. Register stakeholders and their concerns linked to goals.

**Outputs**

- Strategic Intent Package

**Ready to start when**

- Goals drafted

**Complete when**

- Stakeholders identified for primary outcomes

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph1.st3.t2` in **Strategic Framing**

## Model actors and roles

<small>Task definition: `task.cim.ph2.st1.t1`</small>

Model actors and roles This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Strategic Intent Package
- CIM Increment Plan

**How to perform the task**

1. Model human and system Actor elements with trust levels.
2. Define Role elements and link to actors.

**Outputs**

- Participation Model

**Ready to start when**

- Strategic intent captured

**Complete when**

- Actors exist for primary user journeys

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph2.st1.t1` in **Participation Model**

## Register external systems

<small>Task definition: `task.cim.ph2.st1.t2`</small>

Register external systems This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Participation Model
- Strategic Intent Package

**How to perform the task**

1. Register ExternalSystem actors at domain boundaries.

**Outputs**

- Participation Model

**Ready to start when**

- Actors modeled

**Complete when**

- External integrations at boundaries identified

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph2.st1.t2` in **Participation Model**

## Map business capabilities

<small>Task definition: `task.cim.ph2.st2.t1`</small>

Map business capabilities This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Participation Model
- Strategic Intent Package

**How to perform the task**

1. Create BusinessCapability elements linked to goals.
2. Set criticality for each capability in the increment slice.

**Outputs**

- Capability

**Ready to start when**

- Participation model drafted

**Complete when**

- Capabilities cover core value streams for slice

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph2.st2.t1` in **Capability Topology**

## Record capability dependencies

<small>Task definition: `task.cim.ph2.st2.t2`</small>

Record capability dependencies This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Capability
- Strategic Intent Package

**How to perform the task**

1. Model CapabilityDependency relationships between capabilities.

**Outputs**

- Capability

**Ready to start when**

- Capability drafted

**Complete when**

- Dependencies documented for slice

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph2.st2.t2` in **Capability Topology**

## Define domain glossary

<small>Task definition: `task.cim.ph2.st3.t1`</small>

Define domain glossary This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Capability
- Strategic Intent Package

**How to perform the task**

1. Define UbiquitousLanguageTerm entries with definitions and examples.
2. Link terms to capabilities where helpful.
3. Resolve naming conflicts before entity modeling.

**Outputs**

- Ubiquitous Language Glossary

**Ready to start when**

- Capability drafted

**Complete when**

- Glossary covers core domain nouns for slice

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph2.st3.t1` in **Ubiquitous Language**

## Define data classifications

<small>Task definition: `task.cim.ph3.st1.ss1.t1`</small>

Define data classifications This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Ubiquitous Language Glossary
- Strategic Intent Package

**How to perform the task**

1. Create DataClassification elements with confidentiality levels.

**Outputs**

- Information Taxonomy

**Ready to start when**

- Glossary established

**Complete when**

- Classifications cover sensitive categories

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph3.st1.ss1.t1` in **Data Classification**

## Create information items

<small>Task definition: `task.cim.ph3.st1.ss2.t1`</small>

Create information items This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Information Taxonomy
- Ubiquitous Language Glossary

**How to perform the task**

1. Create InformationItem elements for each planned entity attribute group.
2. Set identifiability and data kind on each item.

**Outputs**

- Information Taxonomy

**Ready to start when**

- Classifications defined

**Complete when**

- Information items exist for planned entities

**Checks and evidence**

- CIM-ENTITY-001

**Uses in this process**

- `cim.ph3.st1.ss2.t1` in **Information Items**

## Model domain entities

<small>Task definition: `task.cim.ph3.st2.ss1.t1`</small>

Model domain entities This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Information Taxonomy
- Ubiquitous Language Glossary
- Capability

**How to perform the task**

1. Create DomainEntity elements referencing primary InformationItem identity.
2. Define lifecycle states and business invariants per entity.

**Outputs**

- Domain Structure Model

**Ready to start when**

- Information taxonomy complete

**Complete when**

- Core entities for slice modeled

**Checks and evidence**

- CIM-ENTITY-001

**Uses in this process**

- `cim.ph3.st2.ss1.t1` in **Entities & Lifecycle**

## Model value objects and relationships

<small>Task definition: `task.cim.ph3.st2.ss2.t1`</small>

Model value objects and relationships This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Domain Structure Model
- Information Taxonomy

**How to perform the task**

1. Add ValueObject elements for descriptive data.
2. Model DomainRelationship elements between concepts.

**Outputs**

- Domain Structure Model

**Ready to start when**

- Entities modeled

**Complete when**

- Relationships and VOs complete for slice

**Checks and evidence**

- CIM-RELATIONSHIP-001

**Uses in this process**

- `cim.ph3.st2.ss2.t1` in **Value Objects & Relationships**

## Model commands and outcomes

<small>Task definition: `task.cim.ph3.st3.ss1.t1`</small>

Model commands and outcomes This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Domain Structure Model
- Participation Model
- Capability

**How to perform the task**

1. Create Command elements with actors and target capabilities.
2. Define CommandOutcome and link expected/rejection events.

**Outputs**

- CQRS Behavior Surface

**Ready to start when**

- Domain structure drafted

**Complete when**

- Commands cover primary write use cases

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph3.st3.ss1.t1` in **Commands**

## Model queries

<small>Task definition: `task.cim.ph3.st3.ss2.t1`</small>

Model queries This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- CQRS Behavior Surface
- Information Taxonomy
- Participation Model

**How to perform the task**

1. Create Query elements linked to actors and information items.
2. Set freshness requirements per query.

**Outputs**

- CQRS Behavior Surface

**Ready to start when**

- Commands modeled

**Complete when**

- Queries cover primary read use cases

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph3.st3.ss2.t1` in **Queries**

## Model events, errors, and conditions

<small>Task definition: `task.cim.ph3.st3.ss3.t1`</small>

Model events, errors, and conditions This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- CQRS Behavior Surface
- Domain Structure Model
- Participation Model

**How to perform the task**

1. Create BusinessEvent elements emitted by commands or external systems.
2. Define BusinessError and Condition elements guarding behavior.

**Outputs**

- CQRS Behavior Surface

**Ready to start when**

- Commands and queries modeled

**Complete when**

- Event vocabulary covers slice workflows

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph3.st3.ss3.t1` in **Events & Guards**

## Define aggregate candidates

<small>Task definition: `task.cim.ph4.st1.t1`</small>

Define aggregate candidates This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- CQRS Behavior Surface
- Domain Structure Model
- Capability

**How to perform the task**

1. Create AggregateCandidate groupings with handled commands and emitted events.
2. Document consistency and interaction expectations.

**Outputs**

- Aggregate Boundary Model

**Ready to start when**

- Behavior surface modeled

**Complete when**

- Aggregates align with command/event ownership

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph4.st1.t1` in **Aggregate Boundaries**

## Model business processes

<small>Task definition: `task.cim.ph4.st2.ss1.t1`</small>

Model business processes This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- CQRS Behavior Surface
- Domain Structure Model
- Strategic Intent Package
- Aggregate Boundary Model

**How to perform the task**

1. Create BusinessProcess with step hierarchy and transitions.
2. Connect steps to modeled commands, queries, events, and policies.

**Outputs**

- Business Process Model

**Ready to start when**

- Aggregates defined

**Complete when**

- Key processes orchestrate modeled behavior

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph4.st2.ss1.t1` in **Business Processes**

## Define policies and decision tables

<small>Task definition: `task.cim.ph4.st2.ss2.t1`</small>

Define policies and decision tables This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Business Process Model
- CQRS Behavior Surface
- Domain Structure Model

**How to perform the task**

1. Create Policy elements guarding commands, events, and processes.
2. Model DecisionTable with DecisionRule rows for branching logic.

**Outputs**

- Decision & Policy Model

**Ready to start when**

- Processes drafted

**Complete when**

- Policies linked to behavior elements

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph4.st2.ss2.t1` in **Policies & Decisions**

## Synthesize bounded contexts

<small>Task definition: `task.cim.ph4.st3.t1`</small>

Synthesize bounded contexts This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Capability
- Domain Structure Model
- CQRS Behavior Surface
- Business Process Model
- Decision & Policy Model

**How to perform the task**

1. Group capabilities, entities, CQRS, events, and policies into contexts.
2. Validate memberships reference concrete modeled elements.

**Outputs**

- Bounded Context Map

**Ready to start when**

- Process and behavior complete

**Complete when**

- Contexts have non-empty memberships

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph4.st3.t1` in **Bounded Context Synthesis**

## Formalize requirements

<small>Task definition: `task.cim.ph5.st1.t1`</small>

Formalize requirements This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Requirements Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Strategic Intent Package
- Domain Structure Model
- CQRS Behavior Surface
- Bounded Context Map
- Decision & Policy Model

**How to perform the task**

1. Create Requirement elements with constrains links to domain and behavior.
2. Add AcceptanceCriterion per requirement.

**Outputs**

- Requirements Package

**Ready to start when**

- Bounded contexts synthesized

**Complete when**

- Requirements trace to goals and model elements

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph5.st1.t1` in **Requirements Engineering**

## Apply governance constraints

<small>Task definition: `task.cim.ph5.st1.t2`</small>

Apply governance constraints This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Requirements Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Requirements Package
- Strategic Intent Package
- Participation Model
- Information Taxonomy
- Domain Structure Model

**How to perform the task**

1. Record security, privacy, and compliance constraints on actors and information items.

**Outputs**

- Governance Constraint Package

**Ready to start when**

- Requirements drafted

**Complete when**

- Governance constraints linked to targets

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph5.st1.t2` in **Requirements Engineering**

## Record transformation metadata

<small>Task definition: `task.cim.ph5.st2.t1`</small>

Record transformation metadata This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Governance Constraint Package
- Requirements Package
- Bounded Context Map
- Domain Structure Model
- Decision & Policy Model

**How to perform the task**

1. Record Risk, Assumption, and Hotspot elements on model hotspots.
2. Define TransformationProfile for CIM→PIM with manual decisions.

**Outputs**

- Transformation Contract

**Ready to start when**

- Governance captured

**Complete when**

- Transformation profile ready for ETL

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph5.st2.t1` in **Transformation Contracts**

## Complete trace and readiness

<small>Task definition: `task.cim.ph5.st3.t1`</small>

Complete trace and readiness This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Transformation Contract
- Strategic Intent Package
- Requirements Package
- Domain Structure Model
- CQRS Behavior Surface
- Bounded Context Map
- Governance Constraint Package
- Business Process Model
- Decision & Policy Model

**How to perform the task**

1. Build TraceModel links across goals, requirements, and domain.
2. Complete ProductionReadinessAssessment and resolve findings.

**Outputs**

- Trace & Readiness Record

**Ready to start when**

- Transformation contracts recorded

**Complete when**

- CIM EVL passes; readiness approved

**Checks and evidence**

- cim-semantic-validation

**Uses in this process**

- `cim.ph5.st3.t1` in **Traceability & Readiness Gate**

## Review and adapt CIM increment

<small>Task definition: `task.cim.ph5.st4.t1`</small>

Review and adapt CIM increment This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Trace & Readiness Record
- Strategic Intent Package
- CIM Increment Plan
- Requirements Package
- Governance Constraint Package

**How to perform the task**

1. Review slice outcomes against goals, KPIs, and acceptance criteria.
2. Record accepted scope, deferred work, and stakeholder feedback.
3. Create improvement actions and backlog adjustments for the next engine cycle.

**Outputs**

- CIM Increment Review Record

**Ready to start when**

- CIM EVL passes or all blocking findings are dispositioned

**Complete when**

- Increment accepted or rework loop selected; improvement actions captured

**Checks and evidence**

- Record the owner, status, decision, and evidence links.

**Uses in this process**

- `cim.ph5.st4.t1` in **Increment Review & Adapt**

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
