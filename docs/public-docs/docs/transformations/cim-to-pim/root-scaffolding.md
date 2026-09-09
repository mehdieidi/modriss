# CIM → PIM — Root Scaffolding

Root scaffolding establishes the PIM document that every later rule writes into. It carries the domain identity forward, creates traceability and readiness containers, seeds environments and implementation settings, and turns high-level organizational intent into reviewable PIM policy or readiness evidence. These rules deliberately leave some decisions as review-required rather than pretending that CIM can know a runtime, package manager, or deployment command.

Source module: `mde/transformations/cim-to-pim/root-scaffolding.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## Supporting ETL operations

These operations are not independent source-to-target rules, but they materially shape the result. They derive defaults, create secondary resources, cache correspondences, or resolve relationships after the main rule has run.

| Operation                | Role                                                               | Source                                                   |
| ------------------------ | ------------------------------------------------------------------ | -------------------------------------------------------- |
| `inferArchitectureStyle` | Maps root scaffolding model elements and supporting relationships. | `mde/transformations/cim-to-pim/root-scaffolding.etl:6`  |
| `requirementRuleText`    | Computes requirement rule text.                                    | `mde/transformations/cim-to-pim/root-scaffolding.etl:20` |
| `goalRuleText`           | Computes goal rule text.                                           | `mde/transformations/cim-to-pim/root-scaffolding.etl:35` |
| `glossaryRuleText`       | Computes glossary rule text.                                       | `mde/transformations/cim-to-pim/root-scaffolding.etl:41` |

---

## `CIMModel2PIMModel`

**Source:** `c` — `CIM!CIMModel`  
**Target:** `p` — `PIM!PIMModel`  
**Source location:** `mde/transformations/cim-to-pim/root-scaffolding.etl:51`

### Why this rule exists

This is the root hand-off between modeling levels. It creates the PIM identity, carries the business domain name, chooses an initial architecture style from the CIM's behavior, and creates the trace/readiness containers that later rules need. Without this object, every other refinement would have nowhere reliable to attach its output or explain its origin.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `p` (`PIM!PIMModel`): Transformation root.
- Secondary objects created in the rule body: `KERNEL!TraceModel`, `KERNEL!ProductionReadinessAssessment`.

### Important behavior encoded in the rule

The rule directly assigns: `p.id`, `p.name`, `p.domainName`, `p.defaultCorrelationIdName`, `p.architectureStyle`, `p.traceModel`, `p.readiness`.
Trace identifiers emitted here: `TR-001`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the cimmodel is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/root-scaffolding.etl:51`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `DefaultEnvironmentsAndImplementationProfile`

**Source:** `c` — `CIM!CIMModel`  
**Target:** `dev` — `DEPLOY!Environment`, `test` — `DEPLOY!Environment`, `prod` — `DEPLOY!Environment`, `profile` — `DEPLOY!ImplementationProfile`  
**Source location:** `mde/transformations/cim-to-pim/root-scaffolding.etl:85`

### Why this rule exists

CIM cannot choose a language or package manager, but the PIM still needs explicit environments and a generation profile to be reviewable. This rule creates dev, test, and production-like stages with different approval posture and marks the implementation profile for human completion instead of silently inventing a toolchain.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `dev` (`DEPLOY!Environment`): Generated environment (dev).
- `test` (`DEPLOY!Environment`): Generated environment (test).
- `prod` (`DEPLOY!Environment`): Generated environment (prod).
- `profile` (`DEPLOY!ImplementationProfile`): Generated implementation profile (profile).

### Important behavior encoded in the rule

The rule directly assigns: `dev.id`, `dev.name`, `dev.environmentClass`, `dev.nameSuffix`, `dev.productionLike`, `dev.requiresApproval`, `test.id`, `test.name`, `test.environmentClass`, `test.nameSuffix`, `test.productionLike`, `test.requiresApproval`, `prod.id`, `prod.name`, `prod.environmentClass`, `prod.nameSuffix` ….
Trace identifiers emitted here: `TR-003`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-003`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-003` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the cimmodel actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/root-scaffolding.etl:85`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Requirement2BusinessRule`

**Source:** `r` — `CIMORG!Requirement`  
**Target:** `b` — `POLICY!BusinessRule`  
**Source location:** `mde/transformations/cim-to-pim/root-scaffolding.etl:140`

### Why this rule exists

A requirement becomes a PIM business rule so the intent remains available beside the generated architecture. The text prefers a fit criterion and falls back to Given/When/Then acceptance language; that preserves something testable when later code and contracts are generated.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `b` (`POLICY!BusinessRule`): Generated business rule (b).

### Important behavior encoded in the rule

The rule directly assigns: `b.id`, `b.name`, `b.naturalLanguageRule`.
Trace identifiers emitted here: `TR-004`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.
Manual decisions raised by this rule: `TR-004`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `TR-004` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the requirement actually satisfies its guard and whether the required upstream correspondence exists.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/root-scaffolding.etl:140`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `BusinessGoal2BusinessRule`

**Source:** `g` — `CIMORG!BusinessGoal`  
**Target:** `b` — `POLICY!BusinessRule`  
**Source location:** `mde/transformations/cim-to-pim/root-scaffolding.etl:159`

### Why this rule exists

The goal is carried as a business rule because architecture review needs to retain why a capability exists, not only what it deploys. The transformation chooses the success criterion first, then business value, keeping the PIM rationale connected to the business outcome.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `b` (`POLICY!BusinessRule`): Generated business rule (b).

### Important behavior encoded in the rule

The rule directly assigns: `b.id`, `b.name`, `b.naturalLanguageRule`.
Trace identifiers emitted here: `TR-004`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the business goal is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/root-scaffolding.etl:159`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `UbiquitousLanguageTerm2BusinessRule`

**Source:** `t` — `CIMORG!UbiquitousLanguageTerm`  
**Target:** `b` — `POLICY!BusinessRule`  
**Source location:** `mde/transformations/cim-to-pim/root-scaffolding.etl:172`

### Why this rule exists

A glossary term becomes a guardrail in the PIM. The generated rule records the approved definition and forbidden synonyms so generated APIs, events, schemas, and documentation do not drift into technically convenient but business-inaccurate vocabulary.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `b` (`POLICY!BusinessRule`): Generated business rule (b).

### Important behavior encoded in the rule

The rule directly assigns: `b.id`, `b.name`, `b.naturalLanguageRule`.
Trace identifiers emitted here: `TR-004`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the ubiquitous language term is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/root-scaffolding.etl:172`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `KPI2ReadinessCheck`

**Source:** `k` — `CIMORG!KPI`  
**Target:** `c` — `KERNEL!ReadinessCheck`  
**Source location:** `mde/transformations/cim-to-pim/root-scaffolding.etl:185`

### Why this rule exists

A KPI is not transformed into runtime behavior; it becomes evidence that the model can be reviewed for production. The check records what must be measured and marks itself passed only when metric, target, and source are present, exposing an incomplete business measure instead of hiding it.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `c` (`KERNEL!ReadinessCheck`): Generated readiness check (c).

### Important behavior encoded in the rule

The rule directly assigns: `c.id`, `c.name`, `c.checkId`, `c.severity`, `c.passed`, `c.message`, `c.remediation`.
Trace identifiers emitted here: `TR-004`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the kpi is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/root-scaffolding.etl:185`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Stakeholder2ManualDecision`

**Source:** `s` — `CIMORG!Stakeholder`  
**Target:** `d` — `KERNEL!ManualDecision`  
**Source location:** `mde/transformations/cim-to-pim/root-scaffolding.etl:203`

### Why this rule exists

Stakeholder concerns cannot be resolved by structural mapping alone. This rule preserves the concern as an owned review question attached to the PIM readiness record, so an architectural decision is not lost merely because no PIM class corresponds one-to-one with a stakeholder.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `d` (`KERNEL!ManualDecision`): Generated manual decision (d).

### Important behavior encoded in the rule

The rule directly assigns: `d.id`, `d.name`, `d.question`, `d.decisionOwner`, `d.blocking`, `d.severity`.
Trace identifiers emitted here: `TR-004`. These identifiers are useful when following the generated element back to the originating CIM/PIM decision.

### How to troubleshoot or repair it

Verify that the stakeholder is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

The trace record is the best first diagnostic: it tells you whether the target was created, which transformation rule claimed it, and what source element it came from.

### Authoritative source

Read the complete ETL rule at `mde/transformations/cim-to-pim/root-scaffolding.etl:203`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
