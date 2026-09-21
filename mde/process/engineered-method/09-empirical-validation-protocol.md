# Empirical Validation Protocol

## Purpose

The method-engineering argument establishes that the MODRISS process is systematic,
traceable, internally coherent, and grounded in the supplied literature. It
does not, by itself, show that teams can apply the process effectively. This
protocol defines the empirical work needed before making claims about utility,
efficiency, usability, scalability, or superiority over another approach.

The recommended design is a multiple-case, mixed-method evaluation. Each case
enacts a recorded situational configuration and produces both quantitative
process/product evidence and qualitative practitioner evidence. A single case
can support an initial feasibility claim; contrasting cases are needed to
evaluate configurability and analytic generalization.

## Research questions

| ID     | Question                                                                                                                                                             |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| EV-RQ1 | Can a team enact the tailored MODRISS lifecycle from opportunity framing through a deployed and operated vertical slice?                                             |
| EV-RQ2 | Does the process maintain usable traceability and consistency from requirements/CIM through PIM, PSM, generated artifacts, release evidence, and operational change? |
| EV-RQ3 | Which activities create useful decisions or defect prevention, and which create avoidable burden or duplication?                                                     |
| EV-RQ4 | Does situational tailoring preserve required control objectives while reducing unnecessary work for the case?                                                        |
| EV-RQ5 | How do serverless-specific concerns—cost, events, state, failure, security, cold starts, observability, and provider lock-in—affect decisions and outcomes?          |
| EV-RQ6 | Can the process coordinate ownership and dependencies when more than one team or a platform team is involved?                                                        |

## Propositions and rival explanations

1. Explicit model boundaries and transformation reconciliation will make
   downstream change impact easier to identify. A rival explanation is that
   repository discipline or experienced personnel, rather than the process,
   causes the improvement.
2. Vertical model-driven increments will reveal integration and operational
   risks earlier than completing each abstraction level in bulk. A rival is
   that the selected case is unusually small or familiar.
3. Situational profiles will reduce process burden without losing essential
   evidence. A rival is that teams silently omit work regardless of tailoring
   guidance.
4. Operational evidence routed to the earliest authoritative source will
   reduce unmanaged divergence. A rival is that low change volume makes
   divergence unlikely in either process.

These propositions guide data collection; they are not assumptions to be
confirmed. Negative and contradictory evidence must be retained.

## Case selection

Use purposive maximum-variation sampling rather than selecting only the easiest
demonstration. At minimum:

- **Case A—standard product delivery:** one cross-functional team, moderate
  novelty, one AWS region, ordinary business criticality, and at least one API,
  event flow, workflow, and persistent store;
- **Case B—contrasting situation:** either multiple teams, higher assurance or
  compliance, legacy/external integration, high burst/concurrency, strict
  latency/cost constraints, or a portability requirement.

A case should contain a meaningful change after initial deployment so the
operations-to-model feedback path is exercised. A toy generation-only example
cannot validate the lifecycle claim.

## Unit of analysis and observation points

The primary unit is one configured MODRISS process run for a product or service.
Embedded units are increments, transformations, release candidates, gates,
incidents/changes, work-product revisions, and team dependencies.

Collect evidence at these observation points:

1. baseline and situational tailoring;
2. completion of each increment and gate G0–G5;
3. release authorization, promotion, and transition at G6–G7;
4. an agreed operating window that includes real or controlled workload;
5. at least one change, finding, or incident routed through the process; and
6. retirement/closure if feasible, otherwise a table-top retirement exercise.

## Measures

### Feasibility and conformance

- selected, omitted, substituted, and added fragments with rationale;
- planned versus actually performed tasks and evidence;
- gate outcomes, exceptions, reversals, and time waiting for decisions;
- missing role capabilities and work products that could not be produced;
- deviations classified as beneficial adaptation, misunderstanding, tool gap,
  or method defect.

### Flow and effort

- lead and cycle time per vertical increment;
- active effort by activity family and role;
- queue/wait time at reviews, transformations, dependencies, and environments;
- rework events and the earliest authoritative source to which each returned;
- transformation/generation time versus review and manual-completion time.

### Product and model evidence

- requirements and acceptance items with complete forward/backward trace;
- transformation conflicts, unsupported mappings, manual decisions, and
  unresolved placeholders;
- findings by discovery stage and severity;
- model, generator, code, configuration, release, and runtime divergence;
- escaped defects and changes attributable to incorrect or missing method
  guidance.

### Serverless outcomes

- response latency, availability/error signals, throughput and concurrency;
- cold-start distribution under declared conditions;
- retry, DLQ, idempotency, workflow-failure, and recovery evidence;
- permission/security findings and overly broad access decisions;
- forecast versus observed cost, cost per business unit, and anomalies;
- provider-specific assumptions and estimated migration/exit effort.

### Practitioner experience

After each increment and at case close, collect short ratings and interviews on
clarity, usefulness, duplication, cognitive load, tool support, confidence in
decisions, and willingness to reuse. Ask for concrete examples before asking
for a global satisfaction score. Preserve minority and negative views.

## Data sources and chain of evidence

Use repository revisions, process-run records, method profiles, gate records,
trace reports, transformation/generation manifests, issue/dependency history,
CI/CD logs, test reports, cloud telemetry/cost data, direct observation notes,
and semi-structured interviews. Each reported finding should identify its case,
observation point, source, timestamp/revision, and interpretation.

Maintain a case database with an evidence index. Research notes must distinguish
observed fact, participant explanation, and researcher inference. Sensitive
operational or personal data should be minimized, access-controlled, and
anonymized in published material.

## Analysis

1. Build a chronological account of each case and a requirements-to-evidence
   trace.
2. Compare planned and enacted profiles and explain every material deviation.
3. Use pattern matching against the propositions and explicitly test rival
   explanations.
4. Analyze quantitative measures as distributions and timelines; do not infer
   causality from a small sample or report a misleading aggregate score.
5. Code interview/observation data with a documented scheme. Have a second
   reviewer examine a sample and resolve material disagreements.
6. Perform within-case analysis before cross-case comparison.
7. Re-score the Eidi criteria using the case evidence. Keep design support,
   repository realization, and observed enactment as separate columns.

## Validity safeguards

| Threat                     | Safeguard                                                                                                   |
| -------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Researcher and author bias | Independent evidence review; retain adverse cases; publish the scoring rubric and unresolved disagreements. |
| Hawthorne/novelty effect   | Use repeated increments and an operating/change period, not one guided demonstration.                       |
| Selection bias             | Predeclare case-selection logic and include a contrasting case.                                             |
| Confounding by expertise   | Record participant experience, training, support, and prior familiarity.                                    |
| Instrumentation drift      | Version templates, method profile, tools, metrics, and interview guide for each observation.                |
| Construct validity         | Triangulate process records, repository evidence, telemetry, observation, and interviews.                   |
| External validity          | Bound claims to observed contexts; use analytic rather than statistical generalization.                     |
| Reliability                | Preserve a reproducible case protocol, evidence index, decision log, and analysis trail.                    |

## Review and acceptance rules

At least two assessors should independently rate criterion coverage from a
blinded evidence package. Report raw agreement and disagreements; use a chance-
corrected agreement measure only when the rating distribution and sample size
make it meaningful. Resolve ratings through recorded evidence, not averaging.

The process is ready for broader evaluation when both cases can complete a
release and feedback cycle without an unowned lifecycle gap. A fragment is a
candidate for revision when it repeatedly causes the same misunderstanding,
unnecessary work, missing decision, late defect, or unresolved handoff. A
successful local workaround is not generalized until its context and result
are understood and reviewed.

## Reporting boundary

Report separately:

- what the process **specifies**;
- what the repository **automates or enforces**;
- what participants **actually enacted**; and
- what outcomes were **observed**.

This prevents an implemented generator from being mistaken for a complete
method and prevents a well-written process requirement from being mistaken for
empirical evidence of benefit.
