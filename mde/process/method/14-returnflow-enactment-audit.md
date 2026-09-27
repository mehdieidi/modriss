# ReturnFlow: adversarial enactment and method revision

## Research status and reproducibility

This is a fictional but realistic marketplace returns project, enacted as an
analytical team walkthrough on 2026-09-27. It is not a deployed customer system,
a practitioner experiment, or a completed EMF model. Decisions and failure
injections below are constructed examples. Repository inspections and the
verification commands in `evaluation/returnflow/verification.md` are actual
observations. No latency, cost saving, user satisfaction, or productivity result
is claimed. The baseline inspected was commit
`1d066f44d7c4f7be32b960dbdf5800694639fcfb`.

The earlier ColdChain ledgers enumerate method definitions and attach generated
narrative. Their verifier checks set membership and producer/role bindings; it
does not execute tasks, inspect actual model revisions, or verify gate evidence.
They are useful coverage plans, but cannot demonstrate completed enactment.
This audit retains that distinction and adds concrete decision contents,
counterexamples, and reproducible checks of evidence admissibility.

Research questions: Can a small team identify the next action and its owner?
Can every handoff name a usable input and acceptance criterion? What happens
when evidence is absent, superseded, or adverse? Can operational changes and
retirement preserve authoritative models without creating impossible gates?

The method is an iterative requirements-and-fragment revision, following
Ramsin and Paige [R1], with context, input/output, role and assembly reasoning
from Asadi and Ramsin [R2]. Scenario selection deliberately stresses failure
paths. It is not random sampling, an independent replication, or evidence that
the requirements have stabilized across organizations. Runeson and Höst [R3]
support the separate future natural-context case-study design.

## Situation, team, and intended outcomes

ReturnFlow serves a marketplace with 40 merchants and a fictional demand
assumption of 20,000 return requests/month, with a peak of 30 requests/second
after a campaign. Customers request a return, warehouse staff record receipt,
and an external payment provider refunds an eligible order. Support staff
resolve disputed or uncertain outcomes. Cards and payment credentials are
outside this system. No regulatory certification is assumed.

| Requirement | Acceptance example and measurement plan                                                                                                                                                                                      |
| ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RF-01       | A customer can request a return for their own delivered order within the merchant's 30-day policy; another customer's order is denied.                                                                                       |
| RF-02       | Repeated receipt events and retries must cause at most one refund for the same return and amount. Test concurrent delivery, crash boundaries and replay.                                                                     |
| RF-03       | A timeout with an unknown payment outcome enters reconciliation; it must not automatically create a second payment request with a new key.                                                                                   |
| RF-04       | Customers can see status; staff see an auditable transition history without secrets or card data in logs.                                                                                                                    |
| RF-05       | Proposed request acknowledgement p95 < 1 second at 30 requests/second; accepted refunds reach terminal status within 15 minutes when the provider is available. Measure both cold and warm conditions; no result is assumed. |
| RF-06       | v1 producers/consumers continue working during a later reason-code schema addition. Rollback must preserve already completed refunds.                                                                                        |
| RF-07       | Illustrative records policy: retain refund evidence for 24 months, delete optional photos after 30 days. Custody and budget survive service retirement. These are case assumptions, not legal advice or mandated periods.    |

Six fictional participants combine responsibilities, not decision rights:
Ari (sponsor/product/domain/records), Bea (delivery/method/requirements/business
modeling), Chen (architecture/platform), Dev (application/release), Eli
(quality/process review), and Farah (security/service/FinOps). Eli reviews Dev's
implementation and Chen's models; Farah reviews access and recovery. Ari supplies
domain acceptance. A real pilot must recruit actual merchant, warehouse and
customer representatives; Ari's simulated opinions are not user evidence.

The selected profile is a small, single-provider product team with higher
assurance for refunds and privacy. Two increments share one source repository;
one daily coordination, one evidence review per gate-ready slice, and one
retrospective per increment suffice. Gates can share a review meeting but retain
separate decisions. WIP: two ordinary service items and one expedite, counted
separately; blocked work still counts. Initially use a provisional SLE of 85%
within three working days for standard service work, then recalibrate from
observations. Neither the SLE nor staffing adequacy is validated here.

## Step-by-step team enactment

The following records instantiate the work rather than asserting that a task
name was completed. `v1`, `v2`, and candidate names are scenario identifiers,
not real repository revisions. Each row is a grouped task use: the existing
CIM/PIM/PSM/artifact component supplies the detailed tasks. Conditional
networking, multi-region and multi-cloud work is explicitly deferred; the
team records the omitted capability and reopens it if requirements change.

| Step / process entry   | Team action and tangible output                                                                                                                                                                                                                                                                                                                     | Review, transition, or failure                                                                                                                                                                                                              |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 01 / 0.1               | Ari and Bea write charter: reduce manual refund coordination; exclude payment processing, warehouse management and fraud scoring. Record RF-01–07 and stakeholders.                                                                                                                                                                                 | Ari approves the hypothesis, not a claimed business benefit. Input to feasibility is bounded.                                                                                                                                               |
| 02 / 0.2, MF-02        | Chen compares serverless event processing, a container worker and an existing SaaS returns product. Bursty arrivals favor serverless; provider uncertainty and team learning favor a spike before commitment.                                                                                                                                       | G0 = explore. Farah will review authentication, idempotency and provider outcome-query capability before spending approval.                                                                                                                 |
| 03 / 0.2               | Farah records the cost equation: request/API + function memory-duration + database reads/writes + queue operations + workflow transitions + storage + logging/transfer + provider charges. At 20,000 returns and four domain events/return the base event count is 80,000/month before retries/fan-out; test 1×, 3× and 10× delivery amplification. | No dollar estimate is invented. A real G0 pursue decision requires dated regional tariffs, budget, sensitivity and the provider spike result. In the fictional branch, assume these are reviewed and G0 = pursue; otherwise remain explore. |
| 04 / 0.3               | Bea writes the six-person role mapping, refund-risk assurance, omitted packages, gate evidence rules and training exercise.                                                                                                                                                                                                                         | MF-03 adaptation reduces paperwork by linking one record from multiple tasks; no copied evidence packs.                                                                                                                                     |
| 05 / 0.4–0.5           | Dev owns release execution; Farah owns service response from first production traffic; Eli reviews. Record rollback, secrets, environments, escalation, WIP and acceptance plans.                                                                                                                                                                   | G1 = accept in the scenario. If Farah is unavailable at promotion, G6 must block rather than waiting for G7 to assign responsibility.                                                                                                       |
| 06 / 1.1               | Select I1: request → receipt → refund/status, with RF-01–05 and RF-07. I2 adds reason codes and merchant export, subject to RF-06.                                                                                                                                                                                                                  | One vertical journey has one increment record; no separate three-phase CIM/PIM/PSM project.                                                                                                                                                 |
| 07 / CIM context       | Bea and Ari model Customer, WarehouseAgent and PaymentProvider; Return and OrderReference; request, receipt and refund outcomes; glossary distinguishes requested, received, refund-pending, outcome-unknown and refunded.                                                                                                                          | Reject the word "completed" until domain and payment meanings are separated.                                                                                                                                                                |
| 08 / CIM behavior      | Capture RequestReturn and RecordReceipt commands, ReturnRequested/ParcelReceived/RefundConfirmed events, eligibility policy and an uncertain-refund branch. Add RF-02/RF-03 acceptance examples and RF-05 quality scenario.                                                                                                                         | CIM contains business intent, not Lambda, SQS or database keys. Ari resolves the policy; Chen cannot silently infer it in ETL.                                                                                                              |
| 09 / G2                | Eli checks requirement→behavior→acceptance links, open decisions and the explicit model-validation plan.                                                                                                                                                                                                                                            | Conceptual CIM accepted on paper. Actual G2 remains unperformed until an Ecore-conformant instance and explicit user/model semantic review exist.                                                                                           |
| 10 / 1.3               | Chen plans versioned CIM→PIM ETL; generated draft, previous generated baseline and refined working target are separate.                                                                                                                                                                                                                             | The transformation report must list unresolved mappings. Missing executable models in this analytical study are not disguised as a successful ETL run.                                                                                      |
| 11 / PIM structure     | Define ReturnService, ReceiptConsumer, RefundCoordinator, PaymentAdapter, ReturnStore, event contracts and an asynchronous status query. Boundary rationale: refund coordination owns the external side effect; ingestion must stay responsive.                                                                                                     | No function per noun. Split only for ownership, scaling, security or failure-isolation reasons; record alternatives.                                                                                                                        |
| 12 / PIM behavior      | Chen records ADR-RF-01 below: durable refund intent, stable provider key, outbox, callback/query reconciliation and audit state.                                                                                                                                                                                                                    | First G3 = rework: a local deduplication flag alone does not protect the provider side effect. Revised PIM is conceptually acceptable after ambiguity is resolved.                                                                          |
| 13 / 1.5               | Map to AWS PSM: API Gateway, Lambda, DynamoDB, SQS/DLQ, Step Functions, Secrets Manager, IAM, CloudWatch and S3 evidence. External payment semantics remain an explicit adapter contract.                                                                                                                                                           | An owned unsupported mapping is a blocker until implemented or scope is removed; assigning an owner is not evidence of completion.                                                                                                          |
| 14 / PSM refinement    | Set per-function permissions, bounded retries, DLQ alarms, payload/log redaction, retention and stage separation. Define timeout/visibility/concurrency together and record dated provider limits before deployment.                                                                                                                                | Farah challenges broad permissions and a retention rule that would remove deduplication state before replay expires. G4 = rework until corrected.                                                                                           |
| 15 / G4–generation     | Freeze the accepted PSM, tool/profile versions, configuration and output manifest. Generate into a fresh target; review protected-region changes separately.                                                                                                                                                                                        | For real execution record EMF load, explicit semantic review, EGX result and artifact hashes. No generated refund implementation exists in this paper enactment.                                                                            |
| 16 / 1.8               | Dev implements eligibility, outbox dispatch, provider adapter, status transitions and redaction in supported extension points; Eli writes independent acceptance/fault oracles.                                                                                                                                                                     | Generated test placeholders and zero reported manual actions do not establish business behavior.                                                                                                                                            |
| 17 / 1.9               | Apply test vectors T1–T8 below. Treat provider sandbox behavior, IAM and cloud latency as separate required evidence.                                                                                                                                                                                                                               | G5 = blocked for actual delivery; logical path proceeds only under explicitly assumed passing results. An emulator cannot certify IAM, cost or SLOs.                                                                                        |
| 18 / retrospective     | Bea removes duplicate evidence copying, adds an evidence-ready queue and a next-working-day review/escalation expectation.                                                                                                                                                                                                                          | This is a profile change with an owner, not a new lifecycle phase. Measure whether queue delay falls during the pilot.                                                                                                                      |
| 19 / R1–R2             | Dev assembles candidate c1 from the accepted source/tool/configuration tuple; Eli reviews freshness; Farah accepts the recovery plan and temporary promotion duty.                                                                                                                                                                                  | G6 rejects an injected report for c0. Re-running affected checks on c1 is required. No checkbox carries acceptance across a changed digest.                                                                                                 |
| 20 / R3                | Start a limited rollout with named operator, observation window, abort conditions and recovery. For the first release there is no prior accepted baseline: stop intake, drain/quarantine work and reconcile in-flight provider outcomes.                                                                                                            | Do not "roll back" a refund by reversing an irreversible financial side effect. Stop any duplicate-refund signal immediately; define other stop thresholds before launch.                                                                   |
| 21 / G7                | Farah checks running identity, dashboards, runbook, support access and recovery evidence; accepts c1 as live.                                                                                                                                                                                                                                       | Ongoing Operations starts after G7; release responsibility already covered the interval before it.                                                                                                                                          |
| 22 / O1–O2             | A provider-outage item enters Requested, is triaged with owner and severity, then pulled subject to WIP. Incident class and maintenance purpose are independent.                                                                                                                                                                                    | Restoration and permanent correction are distinct outcomes on one linked timeline.                                                                                                                                                          |
| 23 / O3–O4             | Expedite: temporarily pause refund dispatch. Preserve pending intents and query ambiguous outcomes. Reconcile the emergency setting with authoritative configuration or remove it.                                                                                                                                                                  | Do not close while live state differs from the accepted model/configuration. A terminal state plus a promised follow-up is insufficient.                                                                                                    |
| 24 / O4 bounded change | A CloudWatch threshold needs tuning. It is PSM-owned, so update PSM, regenerate affected artifacts, verify, qualify and hand over a new configuration release.                                                                                                                                                                                      | This is not operations-only just because it is small. Operations-only example: acknowledge an alert and run an approved read-only diagnostic with no persistent configuration change.                                                       |
| 25 / I2 CIM→PIM        | Ari adds optional reason code and export scope; update requirement and contract trace. Preserve unknown/absent code compatibility.                                                                                                                                                                                                                  | G2/G3 are reviewed for changed scope. Unchanged evidence can be reused only after explicit impact review.                                                                                                                                   |
| 26 / I2 PSM→artifact   | Regenerate from new source while the working target contains a manual adapter refinement. Inject an overlapping edit.                                                                                                                                                                                                                               | Block on conflict; Chen resolves meaning, Dev preserves extension logic, Eli reviews old/new consumer tests. Never overwrite the working target to make ETL appear successful.                                                              |
| 27 / G5–G7, release 2  | An injected old-consumer failure rejects c2. Keep c1 live, fix the contract in PIM, propagate, test, and build c3 with a fresh G6 decision.                                                                                                                                                                                                         | In the hypothetical success branch hand over c3. In actual evidence this remains an unexecuted release scenario.                                                                                                                            |
| 28 / O5                | Compare proposed outcomes, flow, incidents, cost/return and model drift with measurements when available; extract reusable adapter guidance only after review.                                                                                                                                                                                      | This walkthrough supplies hypotheses and defect cases, not measured improvement.                                                                                                                                                            |
| 29 / Phase 2.1–2.2     | Ari authorizes a successor migration. Stop new requests, settle or transfer pending refunds, notify consumers, reconcile queues, revoke service credentials and inventory resources.                                                                                                                                                                | G8 rejects when one live consumer or refund obligation remains.                                                                                                                                                                             |
| 30 / Phase 2.3, G8     | Transfer 24-month evidence archive to Ari's named records service with inventory, access policy, budget, expiry and deletion owner; verify restore/access and revoke application access.                                                                                                                                                            | Closure permits accepted transferred custody and its declared archive costs. Unowned obligations block; requiring zero archive cost/access would contradict RF-07.                                                                          |

## Modeling and implementation handoff pack

These are review sheets, not XMI, generated artifacts or a claim of structural
conformance. Their purpose is to expose what the team must decide before a
tool can supply credible evidence. Existing definitions inspected include
`cim-organization.emf` (`Requirement`), `cim-behavior.emf` (`Command`),
`cim-governance.emf` (`QualityScenario`), `pim-policy.emf`
(`IdempotencyPolicy`, `RetryPolicy`, `RetentionPolicy`), `pim-external.emf`
(`ExternalAdapter`), and `awspsm-compute.emf` (`SqsLambdaEventSourceMapping`,
`LambdaAlias`). Class existence does not prove a correct model or domain
sufficiency. The combined Ecore and cross-package relationships still need
actual loading and checking for this case.

| Trace | CIM intent                                | PIM decision                                                  | PSM / implementation obligation                                        | Acceptance oracle                                                     |
| ----- | ----------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------- | --------------------------------------------------------------------- |
| RF-01 | RequestReturn eligibility and ownership   | authenticated principal + order ownership check               | API identity plus application authorization; IAM alone is insufficient | T1: foreign order denied with no persisted intent                     |
| RF-02 | one refund per eligible receipt           | durable intent and stable provider key, not a per-message key | atomic state/outbox write; provider idempotency and reconciliation     | T2–T4: one external effect across duplicates/crashes                  |
| RF-03 | unknown is distinct from failed           | state OUTCOME_UNKNOWN, bounded query/review path              | Step Functions/adapter must not blindly retry with a fresh key         | T3: successful provider action followed by timeout remains one refund |
| RF-05 | timely acknowledgement and completion     | async processing, SLO and backlog signals                     | concurrency, queue age, timeout and cold-start experiment              | T6: timestamped load result with test conditions                      |
| RF-06 | additive reason-code evolution            | optional field; compatible readers; event-version policy      | dual-version contract tests, candidate migration/recovery plan         | T7: old consumer accepts new producer or migration blocks             |
| RF-07 | finite retention with accountable custody | retention and privacy policy per data class                   | storage lifecycle, access separation, restore/deletion runbook         | T8: live access absent, archive transfer accepted                     |

**ADR-RF-01 (PIM v2, hypothetical):** An atomic local transaction stores a refund
intent and an outbox event. The dispatcher may send the event more than once.
Refund attempts use a stable provider key derived from the business return and
refund version; the amount is immutable for that intent. If the provider applies
the refund and the client times out, record OUTCOME_UNKNOWN and query/reconcile
the same key. Never equate a transport timeout with business failure. If the
provider cannot supply durable idempotency and outcome lookup for the maximum
reconciliation period, route to manual resolution and block automatic retries.
Deduplication retention must cover accepted replay/reconciliation periods;
expired keys require explicit reconciliation, not blind replay. This is a
design decision requiring provider evidence, not a guarantee of exactly-once
message delivery. AWS documents duplicate delivery for Lambda/SQS [R4].

T1: unauthorized order; T2: simultaneous duplicate receipt; T3: provider success
followed by timeout before local commit; T4: crash between local transaction and
event publication; T5: poison record with valid records in the same batch;
T6: cold/warm latency and backlog under declared load; T7: old/new contracts
and non-reversible refund during rollback; T8: retention transfer, archive
restore, live-credential revocation and eventual deletion. Expected outcomes
are specified above; none of these application tests was executed in this study.

## Findings and method-engineering decisions

| ID / severity | Baseline evidence and counterexample                                                                                                        | Requirement → fragment and adopted correction                                                                                                                                  | Acceptance test / residual limit                                                                                                                         |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F01 / high    | `build-hypothetical-case.mjs` assigns success strings to every definition; no case files are inspected.                                     | ER-01: distinguish planned, simulated, repository-checked and empirically observed evidence. MF-03/UF-01: relabel inventory and preserve negative results.                     | Generator must not emit performed/reviewed claims by default. New evidence checker rejects an observed claim backed only by simulated evidence.          |
| F02 / high    | `07-evaluation.md` uses A/B for the two Simple criteria and describes Methodology Type without its paper category.                          | ER-02: retain source types and definitions. UF-01: reassess all 78 criteria with source table/page, design/realization and reasons.                                            | 20 general + 24 MDD + 34 serverless rows; two Yes/No criteria; category semantics retained; no aggregate maturity score.                                 |
| F03 / high    | Operations begins at G7, while R3 exposes users before G7; first-release recovery says retain an accepted baseline that does not yet exist. | ER-03: continuous responsibility from first exposure. MF-13/14: G6 names promotion operator and service owner, stop/drain/reconcile plan and first-release recovery.           | Reject promotion without these; established Operations still starts at G7. Human staffing availability remains untested.                                 |
| F04 / high    | G8 prohibits any chargeable resource/access while retention requires funded accessible archives.                                            | ER-04: closure separates live service from accepted records custody. MF-17: transfer register names asset, receiving owner, access, cost, expiry, verification and acceptance. | Unowned archive blocks; accepted funded transfer permits closure. Real legal/contractual decisions remain project-specific.                              |
| F05 / high    | ColdChain calls alarm-threshold tuning operations-only; threshold can be PSM-owned.                                                         | ER-05: route by authoritative ownership, not apparent size. MF-16: persistent model-owned configuration returns through affected generation/release.                           | Runbook diagnostics stay operations-only; modeled threshold change cannot close without reconciliation.                                                  |
| F06 / medium  | Exact revisions are required but there is no reusable explicit invalidation/expiry contract.                                                | ER-06: evidence records scope, revision tuple, provenance and reuse assessment. MF-10/13 + UF-01: a changed dependency or expired exception reopens affected acceptance.       | Stale c0 evidence cannot authorize c1; unchanged evidence needs reasoned reuse approval. The checker is a research instrument, not platform enforcement. |
| F07 / medium  | The existing near-universal A ratings infer completeness from specification and classifier coverage.                                        | ER-07: partial support and unknown effectiveness must remain visible. UF-01: lower ratings where contract completeness or domain coverage is unproven.                         | Compare rubric reasons against actual artifacts. Independent assessors and practitioner cases remain required.                                           |

Alternatives considered: add another universal approval gate (rejected as
duplicating G6), start the entire Operations process at inception (unnecessary;
preparation and temporary promotion responsibility suffice), forbid retained
archives at closure (contradicts obligations), and require every small change
to repeat every task (unnecessary; reuse unaffected evidence with impact
review). The adopted changes adapt existing fragments rather than adding an
unbounded bureaucracy. Each is a local method design hypothesis, grounded by
the construction procedure [R1,R2]; the papers do not prescribe these exact rules.

## Evaluation and future improvement

The complete, corrected Eidi evaluation is [07-evaluation.md](07-evaluation.md).
This exercise demonstrates identifiable handoff defects and more explicit
recovery decisions. It does not demonstrate that companies can yet use the
method smoothly. In particular, ReturnFlow still needs executable CIM/PIM/PSM
instances, actual transformations, application completion, explicit semantic
review, provider sandbox tests, staging/cloud evidence and human gate decisions
before any real G2–G7 acceptance. Paper decisions must never be imported as
production approvals.

Priority order for future work: (1) implement this vertical slice and retain
positive and failing raw evidence; (2) run the unassisted small-team pilot and
measure task comprehension, review delay, evidence-entry effort and rework;
(3) compare against the team's previous practice on matched risk/size, recording
experience and tool support as rival explanations; (4) add enforced revision,
exception-expiry and ownership controls to process-run tooling; (5) test an
independent domain and another provider before claiming domain coverage or
portability; (6) broaden fault and model-derived testing; (7) have independent
assessors rate the same evidence package and preserve disagreements.

The largest weaknesses remain the author-led evaluation, lack of observed
practitioner outcomes, substantial DSML/Epsilon learning, potential gate and
evidence overhead, manual cross-level decisions and application logic, AWS-only
PSM, limited reverse engineering, and incomplete runtime-to-model feedback.
Adding prose improves specification; only enactment with people and executable
artifacts can establish practical usefulness. G0 stop/redirect, deferred scope,
first-release failure and long-lived retained records should remain in future
negative-case protocols, not disappear when a demonstration succeeds.

## References

- [R1] Ramsin, R.; Paige, R. F. (2010). _Iterative criteria-based approach to engineering the requirements of software development methodologies_. IET Software 4(2), 91–104. [Author repository and DOI](https://eprints.whiterose.ac.uk/id/eprint/10815/). Used for requirements-led iterative revision, not as empirical validation of MODRISS.
- [R2] Asadi, M.; Ramsin, R. (2009). _Patterns of Situational Method Engineering_. Studies in Computational Intelligence 253, 277–291. [DOI](https://doi.org/10.1007/978-3-642-05441-9_24). Supplied PDF in `.idea/process_papers`; context/fragment assembly basis.
- [R3] Runeson, P.; Höst, M. (2009). _Guidelines for conducting and reporting case study research in software engineering_. Empirical Software Engineering 14, 131–164. [Paper](https://link.springer.com/article/10.1007/s10664-008-9102-8). Chain of evidence and natural-context validation basis.
- [R4] AWS. _Using Lambda with Amazon SQS_. [Official documentation](https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html), consulted 2026-09-27. Duplicate-delivery premise, not proof of the proposed adapter.
- [R5] Eidi, M.; Ramsin, R. (2026). _Model-Driven Approaches for Serverless Software Development: Evaluation and Future Directions_. MODELSWARD, 560–567. [DOI](https://doi.org/10.5220/0014634200004058). Supplied `.idea/process_papers/Eidi.pdf`, §3, Tables 1–3 pp.563–564 and Tables 4–6 pp.565–566.
