# Climate Relief Grants Case Study

## Purpose

This case study defines a research-grade, real-world evaluation project for the CIM level of the
Varka low-code platform. It is designed to stress the platform's ability to model:

- business goals, KPIs, stakeholders, actors, and roles;
- bounded contexts and ubiquitous language;
- rich domain data with classifications and privacy/compliance obligations;
- command, query, event, condition, policy, and process semantics;
- transformation readiness, traceability, and production-readiness evidence.

The domain is intentionally selected to fit a **serverless, event-driven, compliance-heavy
public-sector workload**: a platform for emergency climate-relief microgrants and supplier
reimbursements.

## Case Study Summary

The modeled system supports a regional public agency that must disburse emergency assistance after
floods, heatwaves, wildfires, and infrastructure failures. The agency receives a surge of citizen
applications, verifies identity and eligibility, coordinates manual review for edge cases, schedules
disbursements through partner payment rails, and manages appeals, audits, and compliance
obligations.

The workload is highly suitable for serverless architecture because it is:

- bursty and unpredictable during disaster events;
- integration-heavy, with multiple external registries and payment services;
- naturally event-driven;
- composed of many short-lived decision, validation, notification, and orchestration steps;
- sensitive to cost, elasticity, auditability, and regional governance controls.

## Why This Is Research Grade

This project is appropriate for rigorous evaluation because it combines:

- high-stakes decision making with traceable business rationale;
- regulated personal and financial data;
- mixed automation and human review;
- long-running, compensating processes;
- bounded contexts and aggregate candidates that force transformation decisions;
- non-functional, privacy, security, and compliance requirements that materially shape architecture;
- transformation risks, assumptions, hotspots, and readiness artifacts.

It is realistic enough to support:

- controlled modeling experiments;
- coverage-oriented metamodel validation;
- transformation testing from CIM to downstream models;
- usability evaluation of the modeling environment;
- semantic validation and regression testing.

## Business Scope

### In scope

- application intake for household emergency assistance;
- evidence capture and missing-document follow-up;
- automated and manual eligibility assessment;
- grant approval or rejection;
- disbursement scheduling and confirmation;
- supplier reimbursement for emergency shelter and essential services;
- citizen appeals and case reopening;
- audit, privacy, compliance, and readiness governance.

### Out of scope

- macro-level budget legislation;
- procurement contract authoring;
- geospatial hazard simulation;
- provider-specific deployment design;
- downstream accounting ledger implementation details.

## Primary Research Questions

1. Can the platform capture business intent, domain language, data semantics, and behavior in one
   coherent CIM?
2. Can one CIM support both operational automation and governance-heavy review scenarios?
3. Does the CIM provide enough semantic precision to guide serverless decomposition later without
   leaking provider-specific design into the business model?
4. Can transformation readiness be assessed from model content rather than ad hoc narrative?
5. Can one evaluation artifact exercise the full CIM metamodel for conformance and regression
   testing?

## Organizational Setting

The modeled organization is a fictional but realistic public agency:

- **Organization**: Regional Climate Assistance Agency
- **Mission**: deliver fast, fair, auditable relief to affected households and approved partner
  providers
- **Operating mode**: surge-oriented public-service operations with strong compliance oversight

## Stakeholders

- affected residents who need rapid assistance;
- case workers and senior reviewers who make accountable decisions;
- finance operations staff responsible for disbursement integrity;
- privacy and compliance officers;
- external payment and identity partners;
- auditors and regulators.

## Business Goals

The case study centers on three strategic goals:

1. **Rapid Eligibility Determination**: determine whether an application can move to decision within
   strict business time windows.
2. **Trustworthy Disbursement**: disburse approved assistance with strong control over fraud,
   duplication, and payment errors.
3. **Auditable Appeals and Oversight**: preserve a defensible record of why decisions were made and
   how contested cases are handled.

## KPI Themes

- median time from submission to initial decision;
- percentage of approved grants disbursed within target window;
- percentage of audited cases with complete decision evidence.

## Bounded Contexts

The case study uses two primary bounded-context candidates:

1. **Intake and Eligibility**

   - application capture
   - document requests
   - identity and household checks
   - eligibility decision support

2. **Disbursement and Appeals**
   - payment scheduling
   - supplier reimbursement
   - appeal submission and resolution
   - audit evidence assembly

These contexts are intentionally distinct but strongly connected through events and shared business
language, making them useful for evaluating service-boundary inference.

## Capability

The core capabilities are:

- **Intake and Eligibility Management**
- **Relief Disbursement Management**
- **Appeals and Oversight Management**

Dependencies are intentionally modeled so that evaluation can inspect capability coupling, critical
paths, and transformation hotspots.

## Domain Concepts

### Entities

- `GrantApplication`
- `ReviewCase`
- `GrantAward`
- `Disbursement`
- `AppealCase`
- `SupplierInvoice`

### Value objects

- `PersonIdentity`
- `PostalAddress`
- `MonetaryAmount`
- `LocationReference`

### Aggregate candidates

- **ApplicationCaseAggregate**

  - root: `GrantApplication`
  - members: `GrantApplication`, `ReviewCase`, `AppealCase`

- **PaymentSettlementAggregate**
  - root: `Disbursement`
  - members: `Disbursement`, `SupplierInvoice`, `GrantAward`

These aggregates are chosen to create deliberate evaluation tension between strong consistency,
eventual consistency, human approval, and compensation.

## Information and Governance Themes

The model includes information items for:

- directly identifying resident data;
- household contact and address data;
- financial routing and disbursement details;
- supporting documents and review notes;
- appeal narratives and audit evidence.

The information landscape intentionally spans several data classes:

- public reference data;
- internal case-management data;
- directly identifying personal data;
- sensitive personal data;
- financial data;
- regulated evidence data.

This allows evaluation of privacy classification, minimization, retention, residency, and security
constraints.

## Behavioral Semantics

The case study includes commands, queries, events, errors, and conditions that mirror real business
activity:

- citizens submit applications and file appeals;
- staff request missing documents and approve or reject grants;
- finance operations schedule disbursements;
- external systems verify identity and confirm fund movement;
- policies react to events and guard decisions;
- queries expose status, search, operational, and audit views.

The behavior is deliberately suitable for event-driven serverless workflows while remaining fully
CIM-level and provider-neutral.

## Business Processes

Two long-running processes are central:

1. **Emergency Grant Case Lifecycle**

   - from intake through eligibility review and approval/rejection

2. **Appeal and Recovery Lifecycle**
   - from appeal intake through reassessment, compensation, and closure

Both processes include start/end, command, query, event, policy, human task, external interaction,
decision, and wait steps so they are useful for full process metamodel coverage.

## Governance and Readiness

The case study intentionally contains:

- measurable non-functional requirements;
- explicit security, privacy, and compliance constraints;
- risks, assumptions, and hotspots;
- a transformation profile expressing serverless-oriented preferences;
- trace links and readiness findings/checks/manual decisions.

This makes the sample suitable not only for modeling demos, but also for transformation-readiness
assessment and regression tests.

## Why Serverless Fits

Serverless architecture is a natural later-stage target because the business problem exhibits:

- highly variable application volume during crisis periods;
- asynchronous event publication and subscription patterns;
- many discrete decision and validation tasks;
- frequent integration boundaries;
- long-running orchestration with waiting periods and compensations;
- strong need for audit logging, elasticity, and pay-per-use economics.

At the same time, the CIM avoids naming concrete providers or products. This is important for
evaluating whether Varka can keep business semantics clean while still producing
transformation-ready models.

## Evaluation Use

This case study is appropriate for:

- metamodel conformance testing;
- semantic validation testing;
- downstream transformation and traceability testing;
- workbench usability studies;
- benchmarking completeness of generated views and editors.

## Deliverables In This Sample

- [`mde/samples/climate-relief-grants-case-study.md`](./climate-relief-grants-case-study.md)
- [`mde/samples/cim.xmi`](./cim.xmi)

## Coverage Note

The XMI sample is built as a **broad-coverage test fixture**. It includes repeated concrete
instances across the major CIM concept families and exercises singleton containers such as
`CIMModel`, `TraceModel`, `TransformationProfile`, and `ProductionReadinessAssessment` through
multiple nested and linked elements. Those singleton containers cannot be instantiated twice because
the metamodel permits only one instance of each under a `CIMModel`.
