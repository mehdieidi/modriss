# PIM validation: Readiness

PIM readiness rules separate a model that can be transformed from one that is ready to be deployed or called production-ready. They reconcile checks, findings, manual decisions, blockers, and remediation rather than treating a single status flag as evidence.

Source profile: `mde/validation/pim/rules/readiness.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `TraceLinkHasReferenceOrExternalId`

**Context:** `KERNEL!TraceLink`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/readiness.evl:8`

### Why this rule exists

The rule checks whether trace link has reference or external id. The trace link element provides the relevant evidence through source, target, source element id, target element id, display name. At this level, transformation readiness and production readiness remain evidence-based decisions rather than optimistic status flags. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-READY-001] TraceLink ' ' has no complete source/target reference.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.source.isDefined() and self.target.isDefined()) or (self.sourceElementId.hasText() and self.targetElementId.hasText())
```

This rule reads: `source`, `target`, `sourceElementId`, `targetElementId`, `displayName`.

### Diagnostic and repair

> [PIM-READY-001] TraceLink ' ' has no complete source/target reference. Fix: link source and target model elements, or provide sourceElementId and targetElementId for external trace endpoints.

**How to fix it:**

link source and target model elements, or provide sourceElementId and targetElementId for external trace endpoints.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionReadyRequiresPassedChecksAndNoBlockingFindings`

**Context:** `KERNEL!ProductionReadinessAssessment`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/readiness.evl:19`

### Why this rule exists

The rule checks whether production ready requires passed checks and no blocking findings. The production readiness assessment element provides the relevant evidence through production ready, transformation ready, deployment ready, findings, blocking. At this level, transformation readiness and production readiness remain evidence-based decisions rather than optimistic status flags. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-READY-002] ProductionReadinessAssessment ' ' is marked productionReady while checks/findings/manual decisions are unresolved.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.productionReady.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.transformationReady.isTrue() and self.deploymentReady.isTrue() and self.findings.forAll(findingItem | not findingItem.blocking.isTrue()) and self.checks.forAll(checkItem | checkItem.passed.isTrue()) and self.manualDecisions.forAll(decisionItem | not decisionItem.blocking.isTrue() or decisionItem.decision.hasText())
```

This rule reads: `productionReady`, `transformationReady`, `deploymentReady`, `findings`, `blocking`, `checks`, `passed`, `manualDecisions`, `decision`, `displayName`.

### Diagnostic and repair

> [PIM-READY-002] ProductionReadinessAssessment ' ' is marked productionReady while checks/findings/manual decisions are unresolved. Fix: pass all checks, resolve blocking findings, and record decisions for blocking manual decisions.

**How to fix it:**

pass all checks, resolve blocking findings, and record decisions for blocking manual decisions.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `FindingShouldRecommendRemediation`

**Context:** `KERNEL!ReadinessFinding`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/readiness.evl:33`

### Why this rule exists

The rule checks whether finding should recommend remediation. It examines message, recommendation, affected elements, display name. Within this part of the model, transformation readiness and production readiness remain evidence-based decisions rather than optimistic status flags. The gap is [PIM-READY-003] ReadinessFinding ' ' lacks message, recommendation or affectedElements. Suggested fix: explain the issue, attach affected elements and provide remediation. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.`message`.hasText() and self.recommendation.hasText() and self.affectedElements.notEmpty()
```

This rule reads: `message`, `recommendation`, `affectedElements`, `displayName`.

### Diagnostic and repair

> [PIM-READY-003] ReadinessFinding ' ' lacks message, recommendation or affectedElements. Suggested fix: explain the issue, attach affected elements and provide remediation.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `FailedCheckShouldHaveRemediation`

**Context:** `KERNEL!ReadinessCheck`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/readiness.evl:43`

### Why this rule exists

The rule checks whether failed check should have remediation. It examines passed, message, remediation, display name. Within this part of the model, transformation readiness and production readiness remain evidence-based decisions rather than optimistic status flags. The gap is [PIM-READY-004] Failed ReadinessCheck ' ' has no message/remediation. Suggested fix: explain what failed and how the user can fix it. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.passed.isDefined() and not self.passed
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.`message`.hasText() and self.remediation.hasText()
```

This rule reads: `passed`, `message`, `remediation`, `displayName`.

### Diagnostic and repair

> [PIM-READY-004] Failed ReadinessCheck ' ' has no message/remediation. Suggested fix: explain what failed and how the user can fix it.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `BlockingManualDecisionMustBeAnswered`

**Context:** `KERNEL!ManualDecision`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/readiness.evl:54`

### Why this rule exists

The rule checks whether blocking manual decision must be answered. The manual decision element provides the relevant evidence through blocking, generated by transformation, decision, decision owner, display name. At this level, transformation readiness and production readiness remain evidence-based decisions rather than optimistic status flags. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-READY-005] Blocking ManualDecision ' ' is unanswered or has no owner.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.blocking.isTrue() and not self.generatedByTransformation.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.decision.hasText() and self.decisionOwner.hasText()
```

This rule reads: `blocking`, `generatedByTransformation`, `decision`, `decisionOwner`, `displayName`.

### Diagnostic and repair

> [PIM-READY-005] Blocking ManualDecision ' ' is unanswered or has no owner. Fix: record the decision and decisionOwner, or mark it non-blocking if it should not stop generation.

**How to fix it:**

record the decision and decisionOwner, or mark it non-blocking if it should not stop generation.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `GeneratedBlockingManualDecisionShouldHaveOwner`

**Context:** `KERNEL!ManualDecision`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/readiness.evl:61`

### Why this rule exists

The rule checks whether generated blocking manual decision should have owner. It examines blocking, generated by transformation, decision owner, affected elements, display name. Within this part of the model, transformation readiness and production readiness remain evidence-based decisions rather than optimistic status flags. The gap is [PIM-READY-006] Generated blocking ManualDecision ' ' should identify an owner or affected PIM elements so it can be triaged. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.blocking.isTrue() and self.generatedByTransformation.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.decisionOwner.hasText() or self.affectedElements.notEmpty()
```

This rule reads: `blocking`, `generatedByTransformation`, `decisionOwner`, `affectedElements`, `displayName`.

### Diagnostic and repair

> [PIM-READY-006] Generated blocking ManualDecision ' ' should identify an owner or affected PIM elements so it can be triaged.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
