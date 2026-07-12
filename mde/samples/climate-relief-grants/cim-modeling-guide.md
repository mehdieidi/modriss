# CIM Modeling Guide: Climate Relief Grants

## 1. Project brief

The Regional Climate Assistance Agency needs a provider-neutral business model for emergency
household grants and supplier reimbursements after floods, heatwaves, and infrastructure failures.
Residents submit evidence, case workers verify eligibility, senior reviewers decide contested
cases, finance staff schedule payments, and external identity and payment partners exchange data.

The workload is bursty, regulated, event-driven, and partly human-operated. The model must preserve
why a decision was made, protect personal and financial data, support appeals, and identify the
business choices that must be resolved before implementation. Provider products, protocols,
programming languages, databases, and deployment topology are deliberately outside the CIM.

### Business outcomes

1. Determine initial eligibility quickly during a disaster surge.
2. Disburse approved funds accurately and without duplicate payments.
3. Preserve complete evidence for appeals and public-funds audits.

### Modeled scope

The finished model contains 3 goals, 3 KPIs, 10 requirements, 3 capabilities, 2 bounded contexts,
6 entities, 4 value objects, 36 information items, 2 aggregates, 8 commands, 4 queries, 10 events,
3 policies, 2 decision tables, and 3 end-to-end processes. It also records governance,
transformation risks, assumptions, hotspots, trace links, and readiness evidence.

## 2. How to use these instructions

Create a CIM model in the Varka workbench and use the element type named in each step from the
palette. Set the element's `id` to the backticked value; later connector steps use those IDs.
All elements inherit `name`, `summary`, `rationale`, and `lifecycleStatus` from the shared kernel.
For this reviewed case study, use `APPROVED` unless a step explicitly says otherwise.

The workbench exposes two kinds of connection:

- A **reference connector** sets a metamodel reference such as `supports`, `issuedBy`, or `payload`.
- A **semantic edge object** creates a contained relationship object. CIM uses these for
  `RequirementRelationship`, `CapabilityDependency`, `DomainRelationship`, `ProcessTransition`, and
  `TraceLink`. Fill the edge's attributes after drawing it.

Nested objects are edited inside their owner: acceptance criteria under a requirement; lifecycle
states and invariants under an entity; outcomes under a command; rules under a decision table;
steps, transitions, exceptions, and temporal constraints under a process; and links under the trace
model. Do not create these as additional CIM roots.

The completed artifact is
[`climate-relief-grants.cim.xmi`](./climate-relief-grants.cim.xmi). It is the canonical source for
every descriptive field and provides a useful checkpoint after each phase.

## 3. Build the model

### Step 1: Create the CIM root

Create `CIMModel` `cim-root`, name it `ClimateReliefGrantsBusinessModel`, and set:

| Attribute          | Value                                                                                                                     |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| `domainName`       | Climate Relief Grants and Reimbursements                                                                                  |
| `businessScope`    | Emergency household microgrants, supplier reimbursements, appeals, and compliance oversight for a regional public agency. |
| `organizationName` | Regional Climate Assistance Agency                                                                                        |
| `modelingDate`     | 2026-05-24                                                                                                                |
| `language`         | en                                                                                                                        |
| `lifecycleStatus`  | APPROVED                                                                                                                  |

Add tags for `research-grade`, `serverless-suited`, `event-driven`, `public-sector`, and
`compliance-heavy`. Add annotations `samplePurpose=Metamodel coverage and evaluation fixture` and
`researchUse=Transformation, validation, and usability evaluation`.

### Step 2: Model intent, measurement, and ownership

Create these `BusinessGoal` nodes under `goals`:

| ID               | Name                            | Priority | Success criterion                                                                            |
| ---------------- | ------------------------------- | -------- | -------------------------------------------------------------------------------------------- |
| `goal-rapid`     | Rapid Eligibility Determination | CRITICAL | Initial decisions meet the declared business-time target.                                    |
| `goal-trust`     | Trustworthy Disbursement        | CRITICAL | Approved assistance reaches the intended recipient without duplicate or unsupported payment. |
| `goal-oversight` | Auditable Appeals and Oversight | HIGH     | Every contested outcome has complete decision and review evidence.                           |

Create one `KPI` per goal: `kpi-decision-time` / Initial Decision Lead Time,
`kpi-disbursement-integrity` / Disbursement Accuracy Rate, and `kpi-audit-completeness` / Audit
Evidence Completeness. Fill `metricDefinition`, `operator`, `targetValue`, `unit`,
`measurementFrequency`, `dataSource`, and `acceptanceThreshold`, then draw `MEASURES` from each KPI
to its matching goal. This sets both `KPI.measures` and the opposite `BusinessGoal.measuredBy`.

Create stakeholders `stakeholder-ops` / Relief Operations Director, `stakeholder-privacy` / Privacy
and Records Office, and `stakeholder-finance` / Public Funds Assurance Team. Fill each concern and
influence level. Connect operations to `goal-rapid`, finance to `goal-trust`, and privacy and finance
to `goal-oversight` with `OWNS_GOALS`.

### Step 3: Capture requirements before solution structure

Create the following requirement nodes. Set `mandatory=true`, give each a verifiable
`fitCriterion`, and connect it to its listed goal with `SUPPORTS_GOALS`.

| ID                  | Concrete type            | Requirement/quality type | Goal                       |
| ------------------- | ------------------------ | ------------------------ | -------------------------- |
| `req-func-apply`    | Requirement              | FUNCTIONAL               | `goal-rapid`, `goal-trust` |
| `req-func-appeal`   | Requirement              | FUNCTIONAL               | `goal-oversight`           |
| `nfr-latency`       | NonFunctionalRequirement | PERFORMANCE              | `goal-rapid`               |
| `nfr-audit`         | NonFunctionalRequirement | AUDITABILITY             | `goal-oversight`           |
| `sec-auth-submit`   | SecurityConstraint       | SECURITY                 | `goal-trust`               |
| `sec-finance`       | SecurityConstraint       | SECURITY                 | `goal-trust`               |
| `priv-resident`     | PrivacyConstraint        | PRIVACY                  | `goal-oversight`           |
| `priv-sensitive`    | PrivacyConstraint        | PRIVACY                  | `goal-oversight`           |
| `comp-public-funds` | ComplianceConstraint     | COMPLIANCE               | `goal-trust`               |
| `comp-appeals`      | ComplianceConstraint     | COMPLIANCE               | `goal-oversight`           |

Under `req-func-apply`, add acceptance criteria for successful intake confirmation and incomplete
submission handling. Under `req-func-appeal`, add appeal evidence capture. Under each NFR, add a
`QualityScenario` with source, stimulus, environment, artifact, response, and measurable response.
Complete the security fields (authentication, authorization, segregation of duties, audit, threat),
privacy fields (law, jurisdiction, legal basis, retention, residency, deletion, minimization), and
compliance fields (regulation, control, evidence, and audit frequency).

Create a `DEPENDS_ON` semantic edge from `req-func-appeal` to `req-func-apply`. Set its edge object
to `reqrel-appeal-apply`, `kind=DEPENDS_ON`, `blocking=false`, and explain that an appeal requires an
original application decision.

### Step 4: Add actors, roles, and trust boundaries

Create four `Actor` nodes and two `ExternalSystem` nodes:

| ID                      | Name                            | Actor type      | Trust level        |
| ----------------------- | ------------------------------- | --------------- | ------------------ |
| `actor-resident`        | Affected Resident               | HUMAN           | UNTRUSTED_EXTERNAL |
| `actor-case-worker`     | Eligibility Case Worker         | HUMAN           | TRUSTED_INTERNAL   |
| `actor-senior-reviewer` | Senior Reviewer                 | HUMAN           | TRUSTED_INTERNAL   |
| `actor-finance-lead`    | Finance Operations Lead         | HUMAN           | TRUSTED_INTERNAL   |
| `ext-identity`          | Civil Identity Registry         | EXTERNAL_SYSTEM | REGULATED_EXTERNAL |
| `ext-payment`           | Public Payment Clearing Partner | EXTERNAL_SYSTEM | REGULATED_EXTERNAL |

For every actor, fill the organization boundary and authentication/authorization expectations. For
the external systems, also fill owner, business purpose, SLA, trust rationale, data-storage and
event-exchange flags.

Create roles `role-resident`, `role-case-worker`, `rolesenior-review`, and `role-finance`. Fill
`responsibility`, `businessPermissionSummary`, and `privileged`; only the senior-review and finance
roles are privileged. Draw `PLAYS_ROLES` from each actor to its matching role.

### Step 5: Draw the capability and context map

Create capabilities `cap-intake` / Intake and Eligibility Management, `cap-disbursement` / Relief
Disbursement Management, and `cap-appeals` / Appeals and Oversight Management. Set responsibilities,
owners, maturity, and criticality; connect them to their goals with `SUPPORTS` and to the relevant
requirements with `REALIZES_REQUIREMENTS`.

Create semantic `CapabilityDependency` edges:

1. `cap-disbursement` to `cap-intake`, ID `capdep-1`, `criticalPath=true`, because payment needs an
   approved eligibility outcome.
2. `cap-appeals` to `cap-intake`, ID `capdep-2`, because appeal review needs the original case.

Create `bc-intake` / Intake and Eligibility Context and `bc-disbursement-appeals` / Disbursement and
Appeals Context. Set language and ownership boundaries, then use `CAPABILITIES` connectors to place
`cap-intake` in the first and the other capabilities in the second.

Create six `UbiquitousLanguageTerm` nodes: Application, Review Case, Grant Award, Disbursement,
Appeal, and Evidence (`term-application` through `term-evidence`). Define each term, synonyms,
forbidden synonyms, and example usage; connect each to its owning context with `CONTEXT`.

### Step 6: Classify information before assigning it to entities

Create seven `DataClassification` nodes:

`class-public` (PUBLIC), `class-internal` (INTERNAL), `class-confidential` (CONFIDENTIAL),
`class-personal` (PERSONAL), `class-sensitive-personal` (SENSITIVE_PERSONAL), `class-financial`
(FINANCIAL), and `class-regulated` (REGULATED).

For each, set identifiability and the encryption, masking, minimization, consent, access-audit,
deletion-right flags and rationale. Direct identifiers are `DIRECTLY_IDENTIFYING`; sensitive and
financial references are at least `INDIRECTLY_IDENTIFYING`; operational/public facts are
`NON_PERSONAL`.

Create these 36 `InformationItem` nodes and set `type`, `required`, example, validation/format,
source of truth, sharing, audit, search, reporting, and retention flags. Connect every item to one
classification with `CLASSIFICATION`.

| Group                    | IDs                                                                                                                                                                                                                                                                                                                                        |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Application and resident | `info-application-id`, `info-household-id`, `info-resident-id`, `info-resident-name`, `info-birth-date`, `info-address`, `info-postcode`, `info-location-reference`, `info-damage-type`, `info-household-size`, `info-income-band`, `info-requested-amount`, `info-submission-time`, `info-vulnerability-note`, `info-supporting-document` |
| Review and decision      | `info-review-case-id`, `info-reviewer-id`, `info-review-notes`, `info-eligibility-score`, `info-decision-rationale`, `info-evidence-reference`                                                                                                                                                                                             |
| Award and payment        | `info-award-id`, `info-award-type`, `info-approval-time`, `info-disbursement-id`, `info-disbursement-amount`, `info-bank-account`, `info-payment-reference`, `info-scheduled-time`, `info-settlement-time`                                                                                                                                 |
| Appeal and supplier      | `info-appeal-id`, `info-appeal-reason`, `info-appeal-submission-time`, `info-invoice-id`, `info-supplier-id`, `info-invoice-amount`                                                                                                                                                                                                        |

Connect personal items to `priv-resident`, vulnerability/evidence items to `priv-sensitive`, payment
items to `comp-public-funds`, and appeal evidence/timestamps to `comp-appeals` using `DATA_ITEMS` or
the constraint's scoped-information connector.

### Step 7: Build the domain model

Create `DomainEntity` nodes `entity-application` / GrantApplication, `entity-reviewcase` /
ReviewCase, `entity-award` / GrantAward, `entity-disbursement` / Disbursement, `entity-appeal` /
AppealCase, and `entity-invoice` / SupplierInvoice. Give each an identity strategy, identity and
lifecycle descriptions, at least one `identityAttributes` reference, exactly one
`primaryIdentityAttribute`, and its remaining `attributes`. Connect each to its owning capability.

Add meaningful nested `LifecycleStateDefinition` values (one initial and at least one terminal) and
`BusinessInvariant` values. Important invariants are no decision without evidence, no payment above
the approved award, and no appeal without an original decision.

Create immutable value objects `vo-person`, `vo-address`, `vo-money`, and `vo-location`; assign their
attributes and equality attributes. Then draw six `DomainRelationship` semantic edges and fill both
nested multiplicities:

1. Application `COMPOSITION` ReviewCase (`rel-app-review`).
2. Application `ASSOCIATION` AppealCase (`rel-app-appeal`).
3. GrantAward `AGGREGATION` Disbursement (`rel-award-disbursement`).
4. GrantAward `OWNERSHIP` SupplierInvoice (`rel-award-invoice`).
5. AppealCase `DEPENDENCY` GrantApplication (`rel-appeal-depends`).
6. PostalAddress `GENERALIZATION` LocationReference (`rel-address-generalization`).

Create `agg-application` / ApplicationCaseAggregate rooted at GrantApplication with application,
review, and appeal members in `bc-intake`. Create `agg-payment` / PaymentSettlementAggregate rooted
at Disbursement with disbursement, invoice, and award members in the second context. Fill consistency
rationale, expectation, conflict resolution, and idempotency business key.

### Step 8: Add business behavior

Create nine `Condition` nodes `cond-complete`, `cond-identity-ready`, `cond-evidence-gap`,
`cond-eligible`, `cond-review-complete`, `cond-bank-verified`, `cond-approved-award`,
`cond-contestable`, and `cond-appeal-review-ready`. Give each natural language plus a FEEL expression
and connect referenced information/concepts.

Create errors `err-eligibility` and `err-destination`, with stable error codes, user-visible meaning,
recoverability, retry, and audit semantics.

Create commands `cmd-submit-app`, `cmd-request-docs`, `cmd-approve`, `cmd-reject`, `cmd-schedule`,
`cmd-record-invoice`, `cmd-file-appeal`, and `cmd-resolve-appeal`. For every command:

1. Set intent, command type, priority, user/idempotency/audit/auth flags and authorization rule.
2. Connect issuing actors, target capability and aggregate, input information, and preconditions.
3. Add nested successful and rejected `CommandOutcome` objects.
4. Connect expected/rejection events and possible errors.

Create queries `qry-status`, `qry-search-apps`, `qry-pending-disbursements`, and `qry-audit-report`.
Set query/freshness type, auth/audit/confidentiality and pagination/filter/sort expectations. Every
query must have at least one `OUTPUT`; connect inputs, read entities, issuing actors, and capability.

Create events `evt-submitted`, `evt-docs-requested`, `evt-approved`, `evt-rejected`,
`evt-disbursement-scheduled`, `evt-funds-disbursed`, `evt-appeal-filed`, `evt-appeal-resolved`,
`evt-identity-verified`, and `evt-invoice-recorded`. Use past-tense semantic names, version `1.0.0`,
business/correlation/causation/ordering keys, visibility/audit/retention flags, payload references,
and affected entities. Connect identity events to `ext-identity` and funds events to `ext-payment`.

### Step 9: Define policies and decisions

Create policies `pol-eligibility` (VALIDATION), `pol-payment` (AUTHORIZATION), and `pol-appeal`
(REACTION). Give each a natural-language rule, FEEL expression, enforcement strength, violation
severity, and audit flag. Connect triggers, guarded commands, constrained queries, and emitted
commands/events.

Create `dt-eligibility` and `dt-appeal` as `DecisionTable` nodes. Connect inputs and outputs, set hit
policy and default outcome, and intentionally set `complete=false` so downstream refinement records
the missing rule-space decision. Add two ordered `DecisionRule` children to each table and connect
their resulting commands/events. Connect each policy to its table with `DECISION_TABLE`.

### Step 10: Model the three end-to-end processes

Create `proc-case-lifecycle`, `proc-disbursement-lifecycle`, and `proc-appeal-lifecycle`. Set trigger,
owner capability/actor/event/command, kind, criticality, completion criterion, long-running,
human-approval, and compensation flags.

Inside each process, create exactly one each of `StartStep`, `CommandStep`, `QueryStep`, `EventStep`,
`PolicyStep`, `HumanTaskStep`, `ExternalInteractionStep`, `DecisionStep`, `WaitStep`, and `EndStep`.
Set sequential `orderIndex`, concrete `stepKind`, responsible roles, and the required command, query,
event, policy, external system, or decision reference. Draw contained `TRANSITION` semantic edges in
business order; label branches and set condition references where applicable.

Add an `ExceptionScenario` and `TemporalConstraint` to each process. The deadlines are the initial
decision window, settlement confirmation window, and appeal resolution window. Connect constrained
elements and record recovery/compensation behavior.

### Step 11: Complete governance and transformation intent

Connect the NFR/security/privacy/compliance nodes from Step 3 to all constrained capabilities,
actors, commands, queries, information, processes, and other scoped elements. This is essential:
standalone governance nodes do not provide enough information for transformation.

Create risks `risk-surge` and `risk-payment`; the payment reconciliation risk is production
blocking. Create assumptions `asm-identity` (accepted) and `asm-provider` (not accepted). Create
hotspots `hot-appeal` and `hot-payment`, mark both as transformation blocking, and attach them to the
affected aggregate/process/capability. Payment compensation also blocks production.

Create singleton `TransformationProfile` `tp-1`. Enable capability-as-service, process-as-workflow,
event-driven collaboration, explicit actor auth, privacy classification, and query read-model
preferences. Explain why capabilities and long-running processes are suitable provider-neutral
boundaries.

### Step 12: Add traceability and readiness

Create singleton `TraceModel` `trace-1`. Inside it create six `TRACE` semantic edges:

1. submission requirement `SATISFIES` rapid goal;
2. latency NFR `CONSTRAINS` case lifecycle;
3. payment risk is `MITIGATES`-related to payment policy;
4. appeal capability `REALIZES` oversight goal;
5. audit query `VALIDATES` public-funds control;
6. payment hotspot `CONFLICTS_WITH` disbursement capability.

Set a transformation rule and confidence on every trace link.

Create singleton `ProductionReadinessAssessment` `ready-1`. Set all three readiness booleans false,
because business decisions remain open. Add findings for payment compensation and identity
availability, checks for information classification and compensation completeness, and blocking
manual decisions for appeal reopening and settlement timeout. Reference the affected elements.

### Step 13: Validate and export

Run the workbench CIM validation and resolve every mandatory and optional violation. Export the model
as XMI. The repository equivalent is:

```powershell
java -jar tools/mde-evl-cli/target/mde-evl-cli-0.0.1-SNAPSHOT.jar cim `
  --repo-root . `
  --model mde/samples/climate-relief-grants/climate-relief-grants.cim.xmi `
  --fail-on-mandatory-violations
```

The checked-in model reports **0 mandatory and 0 optional violations**. It is complete enough for
CIM-to-PIM transformation while honestly retaining explicitly modeled business hotspots.

## 4. Reproduce the transformation

```powershell
java -jar tools/mde-etl-cli/target/mde-etl-cli-0.0.1-SNAPSHOT.jar cim-to-pim `
  --repo-root . `
  --source-model mde/samples/climate-relief-grants/climate-relief-grants.cim.xmi `
  --target-model mde/samples/climate-relief-grants/climate-relief-grants.pim.xmi `
  --overwrite `
  --log-file target/climate-relief-grants-etl-report.json
```

This command was run for the case study. The generated PIM is the input to the refinement guide.
