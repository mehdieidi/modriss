# PIM Refinement Guide: Climate Relief Grants

## 1. Starting point

[`climate-relief-grants.pim.xmi`](./climate-relief-grants.pim.xmi) was produced from the case-study
CIM by the repository's `cim-to-pim` ETL. Do not recreate it manually. Import it as a PIM model,
retain its `generatedFrom`, `traceId`, incoming/outgoing traces, and generated rationale, and refine
the generated objects in place. Mark reviewed edits `manuallyMaintained=true`, set an appropriate
`lifecycleStatus`, and add review notes; do not sever CIM traceability.

The generated model is structurally and semantically valid: PIM EVL reports **0 mandatory and 0
optional violations**. Validity is not the same as implementation completeness. Its readiness is
`TRANSFORMATION_READY`, with `deploymentReady=false`, `productionReady=false`, several blocking
manual decisions, and a default implementation profile containing `CUSTOM`, `NONE`, and `TBD`.

### Generated architecture inventory

- Services: Intake and Eligibility Context Service; Disbursement and Appeals Context Service.
- APIs: one API per service, with routes derived from commands and queries.
- Compute: 8 command handlers, 4 query handlers, 3 policy handlers, 2 decision evaluators, and 3
  human-task handlers.
- Workflows: Emergency Grant Case Lifecycle, Disbursement and Recovery Lifecycle, and Appeal and
  Recovery Lifecycle.
- Data: 2 aggregate stores and 4 generated read stores, plus their access objects and schemas.
- Integration: 2 external adapters/endpoints, 20 event types, 49 channels, 31 flows, and 14 triggers.
- Operations: `dev`, `test`, and `prod`, 2 deployment units, and 2 service configuration sets.

The following steps turn that generated architecture into a complete provider-independent PIM. The
choices are intentionally concrete enough for the next PIM-to-PSM transformation while naming no
cloud product.

## 2. Refine in dependency order

### Step 1: Baseline and protect generated intent

1. Create a refinement branch/version of the generated PIM; never rerun ETL over reviewed manual
   edits without first preserving this version.
2. Open the Traceability view and confirm both services, every function, workflow, schema, store,
   adapter, policy, and readiness item retain a source trace to the CIM.
3. Keep the two bounded-context services. Set both `boundaryType=BOUNDED_CONTEXT_BASED`.
4. Set Intake and Eligibility's owner team to `Relief Case Platform Team`; set Disbursement and
   Appeals' owner team to `Funds and Oversight Platform Team`.
5. Keep Intake externally exposed for resident/case-worker routes. Keep Disbursement and Appeals
   externally exposed only for appeal intake; finance/audit routes remain authenticated internal
   routes.
6. Review every `ServiceElementMembership`. A deployable element must have one owning service;
   cross-service use is represented by a flow or `DEPENDS_ON` membership, not duplicate ownership.

### Step 2: Complete the implementation profile

Edit `implementation_profile_default`:

| Attribute                   | Required value                                                                                        |
| --------------------------- | ----------------------------------------------------------------------------------------------------- |
| `primaryLanguage`           | GO                                                                                                    |
| `languageVersion`           | 1.24 or the organization-approved supported version                                                   |
| `packageManager`            | GO_MOD                                                                                                |
| `sourceLayout`              | `cmd/<function>/main.go; internal/{domain,application,ports,adapters}; contracts/`                    |
| `testFramework`             | Go testing package with table-driven unit and contract tests                                          |
| `lintCommand`               | `golangci-lint run ./...`                                                                             |
| `formatCommand`             | `gofmt -w .`                                                                                          |
| `buildCommand`              | `go build ./...`                                                                                      |
| `dependencyPolicy`          | Pin direct dependencies; prohibit provider SDK imports outside PSM adapters; scan and review updates. |
| `generateTypedContracts`    | true                                                                                                  |
| `generateRuntimeValidation` | true                                                                                                  |

Set its lifecycle to `APPROVED` and resolve the implementation-profile manual decision. This removes
the `CUSTOM` runtime condition that currently prevents `deploymentReady`.

### Step 3: Confirm service contracts and API surface

For both generated APIs, retain `RESOURCE_ORIENTED_HTTP`, set version `1.0.0`, base paths `/relief/v1`
and `/funds-oversight/v1`, require generated OpenAPI, and attach the generated `ApiContract`.

Review every generated route and make its operation explicit:

| Business operation       | Method and path                                        | Integration                                  |
| ------------------------ | ------------------------------------------------------ | -------------------------------------------- |
| SubmitGrantApplication   | `POST /applications`                                   | command handler                              |
| RequestMissingDocuments  | `POST /applications/{applicationId}/document-requests` | command handler                              |
| ApproveEmergencyGrant    | `POST /applications/{applicationId}/approval`          | case workflow or command handler, not both   |
| RejectEmergencyGrant     | `POST /applications/{applicationId}/rejection`         | case workflow or command handler, not both   |
| GetApplicationStatus     | `GET /applications/{applicationId}`                    | query handler                                |
| SearchApplications       | `GET /applications`                                    | query handler                                |
| ScheduleDisbursement     | `POST /awards/{awardId}/disbursements`                 | disbursement workflow                        |
| RecordSupplierInvoice    | `POST /awards/{awardId}/supplier-invoices`             | command handler                              |
| ListPendingDisbursements | `GET /disbursements?status=pending`                    | query handler                                |
| FileAppeal               | `POST /applications/{applicationId}/appeals`           | appeal workflow                              |
| ResolveAppeal            | `POST /appeals/{appealId}/resolution`                  | appeal workflow or command handler, not both |
| GenerateCaseAuditReport  | `GET /audit/cases/{applicationId}`                     | query handler                                |

For each `ApiRoute`, set a unique `operationId`, expected success status, consumer description,
request/response validation flags, and exactly one of `functionIntegration` or `workflowIntegration`.
Set pagination to cursor-based for search/list routes. Attach request, response, and error schemas;
map `err-eligibility` to a client-validation status class and `err-destination` to a conflict or
unprocessable-operation class without encoding provider-specific gateway behavior.

### Step 4: Finish schemas and contracts

Review all 76 generated schemas rather than treating generated shapes as final:

1. Set semantic version `1.0.0` and compatibility `BACKWARD` on public request, response, and event
   schemas. Disallow additional properties unless extension data is an explicit business feature.
2. Confirm every identifier maps to `UUID` or `STRING` consistently. Preserve money as `MONEY` or a
   value object with decimal amount and ISO currency; never use binary floating point semantics.
3. Complete `SupportingDocument` as an object schema containing document ID, media type, integrity
   digest, storage reference, submitted time, and classification. Store content outside event and
   command payloads; carry only metadata/reference.
4. Apply min/max, regex, enum values, required/nullable, array cardinality, examples, and consumer
   descriptions from the CIM information items.
5. Mark resident identity fields personal, vulnerability/evidence fields sensitive, and bank/payment
   fields financial. Confirm no secret value appears in a schema.
6. For each `FunctionContract`, attach input/output/error schemas, require correlation IDs, require
   auth context on protected operations, and set the idempotency key field on submission, payment,
   invoice, appeal, and resolution commands.
7. For every event type, retain an envelope with event ID, type, source, timestamp, version,
   correlation ID, causation ID, and subject fields. Confirm schema and envelope are both present.

Resolve the SupportingDocument manual decision after its object structure and validation rules are
approved.

### Step 5: Refine aggregate and read storage

Keep `ApplicationCaseAggregate Store` and `PaymentSettlementAggregate Store` as source-of-truth
stores. Choose `DOCUMENT` plus `TRANSACTIONAL` consistency for each aggregate boundary, encrypted,
persistent, write optimized, with point-in-time recovery. Confirm the application store contains
personal data and both stores have data-protection, retention, backup, and access-audit policies.

For the four read stores:

| Store                               | Kind      | Freshness and rebuild decision                                                 | Primary index candidate           |
| ----------------------------------- | --------- | ------------------------------------------------------------------------------ | --------------------------------- |
| GetApplicationStatus Read Store     | KEY_VALUE | near real time; replay application events                                      | `applicationId`                   |
| SearchApplications Read Store       | SEARCH    | eventually consistent; full event replay plus checkpoint                       | context filters + submission time |
| ListPendingDisbursements Read Store | DOCUMENT  | near real time; replay payment events                                          | status + scheduled time           |
| GenerateCaseAuditReport Read Store  | DOCUMENT  | eventually consistent but complete; rebuild from immutable audit/event history | application ID + occurred time    |

Set volumes, expected access rates, read/write optimization, consistency, and recovery requirements.
Complete each contained `DataModel`, `DataField`, `AccessPattern`, and `IndexCandidate`. Link access
patterns to the actual query/command functions, set expected item counts and latency targets, and
verify every `DataAccess` has the correct mode, function, store, model, and access pattern.

Resolve all four projection decisions. For each generated domain-relationship decision, document
the physical choice: embed review summaries under application, reference appeals by application ID,
reference disbursements by award ID, and reference supplier invoices by award ID. Keep full records
in their aggregate-owned model; projections may denormalize read-only fields.

### Step 6: Make function execution characteristics explicit

Review all 20 functions. Keep command/query/policy/decision/human-task kinds generated by ETL, then
set responsibility, source name, execution model, state access, event publication, adapter calls,
and operational estimates.

- Synchronous API handlers: `REQUEST_RESPONSE`, `IO_BOUND`; set P95 targets below their route timeout.
- Event/policy handlers: `ASYNC_EVENT`; require idempotency and attach retry/dead-letter policy.
- Decision evaluators: `LIGHTWEIGHT`, stateless, with no direct data write.
- Human-task handlers: `WORKFLOW_TASK`; persist callback/task correlation and require idempotency.
- Payment and identity callers: require network access and call only their explicit adapter.

For each function set expected average/P95 duration and peak concurrency based on the latency NFR and
surge estimate. Attach timeout, concurrency, resilience, observability, and security policies. Public
entry points must have security policy coverage. No function may read/write a store without a
matching `DataAccess`.

### Step 7: Complete event channels, flows, and triggers

Consolidate the generated 49 channels where multiple generated flows express the same business
event boundary. Keep distinct channels where ownership, security, delivery, ordering, or retention
differs; preserve trace links when merging.

Use `TOPIC` for externally useful domain-event fan-out and `QUEUE` for a single work owner. Set:

- `EFFECTIVELY_ONCE_WITH_IDEMPOTENCY` for application submission, approval, payment, invoice, and
  appeal processing;
- `AT_LEAST_ONCE` for notifications/read-model updates that are idempotent;
- `PER_KEY` ordering using application ID, award ID, disbursement ID, or appeal ID as appropriate;
- explicit retention, replay, dead-letter, encryption, and personal-data flags.

For all 31 `Flow` objects, confirm source, target, event/schema, synchronous/asynchronous mode,
purpose, ownership, and error path. For all 14 triggers, set enabled, source, exactly one function or
workflow target, invocation mode, and an event filter where the channel carries multiple event
types. Attach resilience and batch policy where needed. Remove accidental duplicate flows only after
comparing their trace provenance.

### Step 8: Complete decision models and business rules

The ETL intentionally carried both CIM decision tables forward as incomplete. Refine the Eligibility
Decision Model with exhaustive, mutually understood outcomes for eligible-complete, incomplete
evidence, identity unavailable, ineligible, and manual-review-required. Refine the Appeal Decision
Model for timely documentary appeal, late appeal with accepted exception, payment dispute, material
new evidence, conflict of interest, and full senior review.

For every contained decision rule, provide required condition and outcome expression models, rule
priority, input/output schemas, and resulting routing behavior. Select an explicit hit policy and
prove coverage using table tests. Once no meaningful input combination lacks an outcome, resolve the
two blocking incomplete-decision-table decisions. Link generated business rules to the enforcing
policy/decision functions and input/output schemas.

### Step 9: Refine workflow orchestration and human work

For each generated workflow, verify one start state, at least one terminal state, reachable states,
default choice branches, and transitions matching the CIM process. Set execution semantics,
maximum duration, state timeouts, retry/catch behavior, input/output mappings, idempotency,
observability, and compensation.

Use callback-style human tasks rather than holding compute:

| Task                       | Assignee                | SLA              | Escalation                                       |
| -------------------------- | ----------------------- | ---------------- | ------------------------------------------------ |
| Reviewer Assessment Step   | Eligibility Review Role | 4 business hours | Senior Decision Role after 3 hours               |
| Finance Investigation Step | Finance Control Role    | 1 business day   | Public Funds Assurance owner after 12 hours      |
| Appeal Review Step         | Senior Decision Role    | 10 business days | Relief Operations Director after 8 business days |

Create/attach `HumanTask` or `ApprovalTask`, completion-evidence schema/event, timeout, and
`EscalationPolicy`; connect workflow states to them. Resolve all three human-task manual decisions.

Adopt this business resolution for the appeal hotspot: a successful appeal emits a corrective
outcome and starts a versioned reassessment branch; it does not mutate or erase the original
decision. Adopt this payment compensation: after a 24-hour settlement timeout, freeze automatic
retry, open Finance Investigation, reconcile by idempotency key, and emit either FundsDisbursed or
DisbursementFailed; notify the resident only from the authoritative final event. Attach explicit
`CompensationPolicy`, events, and handlers, then resolve both hotspot decisions.

### Step 10: Complete external integrations and credentials

For `Civil Identity Registry Endpoint`, set protocol family `HTTPS/REST`, private network according
to the inter-agency agreement, credentials required, rate-limit flag, SLA, and a configuration-based
URI. Use a machine credential with signed client assertion or mTLS certificate; never store a
credential literal in the model.

For `Public Payment Clearing Partner Endpoint`, set protocol family `HTTPS/REST`, credentials
required, rate-limited, private-network expectation, SLA, and configuration-based URI. Use mTLS plus
short-lived token where the contract requires it.

For both adapters:

1. Attach exactly one endpoint and typed credential requirements linked to generated reference-only,
   environment-specific secrets.
2. Add timeout, bounded exponential retry, circuit breaker, idempotency, structured logs, metrics,
   traces, alerts, and an SLO.
3. Classify retryable/non-retryable errors. Payment submission retries must reuse the same business
   idempotency key.
4. Define integration flow ownership and degraded behavior. Identity failure routes to manual
   verification; payment uncertainty routes to finance investigation, never blind re-submission.

Resolve both protocol/credential decisions, the identity availability assumption, and the provider
registry assumption only after contract tests and degraded-mode tests pass.

### Step 11: Finish identity, authorization, and least privilege

Replace generic generated identity-provider assumptions with provider-independent intent:

- Affected Resident: federated/citizen identity, MFA step-up for sensitive changes.
- Case Worker, Senior Reviewer, Finance Lead: workforce federation, MFA required, validated issuer,
  audience, token claims, and group/role mapping.
- External systems: machine-client principals authenticated by certificate or signed client token.

For each of the four role principals, create explicit contained `Permission` objects. Set `ALLOW`,
an enum `actionKind`, provider-neutral `action`, concrete PIM `targetResource`, conditions,
least-privilege rationale, and `CONFIRMED_LEAST_PRIVILEGE` only after review. Minimum grants are:

- Resident: submit/read own application; submit/read own appeal; no list, approval, or payment access.
- Case worker: read/update assigned cases, request evidence, evaluate eligibility; no payment action.
- Senior reviewer: approve/reject and resolve appeals; cannot schedule/confirm payment.
- Finance control: read approved awards and schedule/reconcile payments; cannot alter eligibility.

Attach `AuthPolicy` to APIs, `AuthorizationPolicy` to every protected route, and `SecurityPolicy` to
functions, workflows, stores, and adapters. Encode resource-level ownership/assignment conditions.
Model dual control so the payment initiator cannot be its final approver. Resolve the four role and
two security-constraint permission decisions only after the permission matrix has no broad wildcard
action/resource.

### Step 12: Quantify operational architecture policies

Replace generated review placeholders with measured values:

1. Translate Decision Latency Target into route/function/workflow timeout budgets, peak concurrency,
   queue-age alarm, and rate limits. Document the surge-volume assumption used for each value.
2. For initial decision, settlement, and appeal temporal constraints, set ordering keys,
   idempotency scope/expiration, timeout, retry ceiling, and escalation. Use 24 hours as settlement
   confirmation timeout and the business-approved windows stored in the CIM for the other two.
3. Configure structured JSON logging with correlation/causation IDs and sensitive-field masking.
4. Add metrics for submission count/error/latency, decision lead time, queue age, approval outcome,
   payment settlement latency/failure, appeal age, and dead-letter depth.
5. Add SLOs and alerts derived from the three KPIs; specify target, window, notification owner, and
   error-budget response.
6. Complete backup/restore RPO and RTO, retention/deletion/legal-hold, encryption, residency,
   evidence, and access-audit policies for each store and channel.

Resolve the latency, temporal-ordering, and critical capability-dependency decisions after load,
failure, replay, and recovery tests demonstrate these values.

### Step 13: Complete environment, configuration, and deployment intent

Keep `dev`, `test`, and `prod`. Set `productionLike=true` for test and prod where test exercises
production controls; require approval for prod. Never put endpoint URIs, credentials, or personal
data in default values.

For both service configuration sets, retain `LOG_LEVEL` and `CORRELATION_ID_NAME`, then add typed,
validated parameters/environment variables for endpoint URI references, timeout values, rate limits,
feature flags, projection checkpoints, and alert destinations. Use `Secret` references for all
credentials, with owner, rotation requirement/frequency, and environment separation.

Review both deployment units. Keep independent service releases and semantic versioning, include
all owned deployables, target all intended environments, and ensure nothing is orphaned. Document
contract compatibility and event-schema rollout order for cross-service releases.

### Step 14: Close readiness with evidence

Process every generated `ReadinessFinding` and `ManualDecision`; do not merely delete or mark it
non-blocking. Record the decision, owner, evidence/trace, review notes, and affected elements.

The PIM may be marked deployment ready only when:

- implementation profile no longer contains `CUSTOM`, `NONE`, or `TBD`;
- APIs/routes and function contracts are complete and uniquely integrated;
- stores have models, access patterns, indexes, protection, backup, and retention;
- events have schemas/envelopes and channels have delivery/ordering decisions;
- workflows have reachable transitions, errors, timeouts, human tasks, and compensation;
- external adapters have endpoint, credential, resilience, idempotency, and observability decisions;
- permissions are explicit and reviewed for least privilege;
- deployment units contain all deployable service elements;
- every blocking manual decision has a real decision and supporting evidence;
- every blocking finding is resolved or replaced by a reviewed non-blocking residual risk.

Set the readiness booleans from evidence: first `transformationReady`, then `deploymentReady`, and
only set `productionReady` after implementation, security, privacy, operational, recovery, and
business acceptance evidence exists. A complete PIM can be deployment-ready without falsely claiming
that unbuilt software is production-ready.

### Step 15: Revalidate the refined PIM

Export the refined PIM and run:

```powershell
java -jar tools/mde-evl-cli/target/mde-evl-cli-0.0.1-SNAPSHOT.jar pim `
  --repo-root . `
  --model path/to/refined-climate-relief-grants.pim.xmi `
  --fail-on-mandatory-violations `
  --fail-on-optional-violations
```

Review the trace and readiness views once more. The final PIM is ready for PIM-to-PSM transformation
when validation is clean, all required platform-independent choices above are explicit, and no
blocking decision is hidden behind a placeholder.
