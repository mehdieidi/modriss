# PIM validation: Core

PIM core rules check domain and implementation-profile completeness, architecture-style consistency, model identity, generated-element traceability, and rationale for manually maintained elements.

Source profile: `methodology/modeling-framework/validation/pim/rules/core.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `DomainNameRequired`

**Context:** `PIM!PIMModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `methodology/modeling-framework/validation/pim/rules/core.evl:8`

### Why this rule exists

The rule checks that the PIM declares its business domain. This gives generated artifacts and reports a stable domain context. Missing evidence would leave generated names and reports without a consistent context. The rule prevents the following failure: [PIM-ROOT-002] The PIM model is missing a domainName.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.domainName.hasText()
```

This rule reads: `domainName`.

### Diagnostic and repair

> [PIM-ROOT-002] The PIM model is missing a domainName. Fix: provide a stable domain or bounded-context name so generated artifacts and reports can be named consistently.

**How to fix it:**

provide a stable domain or bounded-context name so generated artifacts and reports can be named consistently.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ImplementationProfileRequiredForGeneration`

**Context:** `PIM!PIMModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `methodology/modeling-framework/validation/pim/rules/core.evl:14`

### Why this rule exists

The rule checks that an implementation profile is present. The profile gives artifact generation explicit language, package, and build choices. Missing evidence would leave generation without a concrete implementation setup. The rule prevents the following failure: [PIM-ROOT-003] Artifact generation requires an ImplementationProfile.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.implementationProfile.isDefined()
```

This rule reads: `implementationProfile`.

### Diagnostic and repair

> [PIM-ROOT-003] Artifact generation requires an ImplementationProfile. Fix: add one with primaryLanguage, packageManager, sourceLayout, test/build commands and contract-generation choices.

**How to fix it:**

add one with primaryLanguage, packageManager, sourceLayout, test/build commands and contract-generation choices.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DefaultCorrelationIdShouldBeNamed`

**Context:** `PIM!PIMModel`  
**Classification:** Advisory critique (warning)  
**Source:** `methodology/modeling-framework/validation/pim/rules/core.evl:19`

### Why this rule exists

The rule advises that a default correlation identifier name be defined. A shared name lets APIs, events, workflows, and logs use a consistent correlation convention. The gap is No defaultCorrelationIdName is defined. Suggested fix: set a name such as correlationId. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.defaultCorrelationIdName.hasText()
```

This rule reads: `defaultCorrelationIdName`.

### Diagnostic and repair

> No defaultCorrelationIdName is defined. Suggested fix: set a portable name such as correlationId so APIs, events, workflows and logs can share the same correlation convention.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ArchitectureStyleShouldMatchContents`

**Context:** `PIM!PIMModel`  
**Classification:** Advisory critique (warning)  
**Source:** `methodology/modeling-framework/validation/pim/rules/core.evl:24`

### Why this rule exists

The rule checks whether the selected architecture style matches the APIs, events, channels, and workflows in the model. The gap is The selected architectureStyle does not match the model contents. Suggested fix: add the matching APIs/events/workflows or choose HYBRID_SERVERLESS if multiple styles are intentionally combined. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (not self.architectureStyle.enumIs("API_FIRST_SERVERLESS") or self.allServiceApis().notEmpty()) and (not self.architectureStyle.enumIs("EVENT_DRIVEN_SERVERLESS") or self.eventTypes.notEmpty() or self.allServiceChannels().notEmpty()) and (not self.architectureStyle.enumIs("WORKFLOW_ORCHESTRATED_SERVERLESS") or self.allServiceWorkflows().notEmpty())
```

This rule reads: `architectureStyle`, `allServiceApis`, `eventTypes`, `allServiceChannels`, `allServiceWorkflows`.

### Diagnostic and repair

> The selected architectureStyle does not match the model contents. Suggested fix: add the matching APIs/events/workflows or choose HYBRID_SERVERLESS if multiple styles are intentionally combined.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ModelElementIdRequired`

**Context:** `KERNEL!ModelElement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `methodology/modeling-framework/validation/pim/rules/core.evl:37`

### Why this rule exists

The PIM model is a transformation boundary, so an element without an ID cannot be reliably matched with its CIM origin or with a later generated revision. This rule protects synchronization, not cosmetic naming.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.`id`.hasText()
```

This rule reads: `id`, `displayName`.

### Diagnostic and repair

> [PIM-NAME-001] Element id is missing. Fix: generate an id for ' '.

**How to fix it:**

generate an id for ' '.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `GeneratedElementShouldBeTraceable`

**Context:** `KERNEL!TraceableElement`  
**Classification:** Advisory critique (warning)  
**Source:** `methodology/modeling-framework/validation/pim/rules/core.evl:48`

### Why this rule exists

Generated content must remain explainable. The rule prevents a transformed PIM/PSM object from becoming an orphan that a reviewer cannot connect back to the decision that created it.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.generatedByTransformation.isTrue() and not self.isKindOf(KERNEL!TraceLink) and not self.isKindOf(KERNEL!TraceModel) and not self.isKindOf(KERNEL!ProductionReadinessAssessment) and not self.isKindOf(KERNEL!ReadinessFinding) and not self.isKindOf(KERNEL!ReadinessCheck) and not self.isKindOf(KERNEL!ManualDecision) and not self.isGeneratedTraceSupportElement()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.generatedFrom.hasText() or self.sourceReference.hasText() or self.sourceUri.hasText() or self.traceId.hasText()
```

This rule reads: `generatedByTransformation`, `isKindOf`, `isGeneratedTraceSupportElement`, `generatedFrom`, `sourceReference`, `sourceUri`, `traceId`, `displayName`.

### Diagnostic and repair

> [PIM-TRACE-001] Generated element ' ' has no trace back to its source. Suggested fix: fill generatedFrom, sourceReference, sourceUri or traceId so users can understand where the element came from.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RationaleRecommendedForManuallyMaintainedElements`

**Context:** `KERNEL!TraceableElement`  
**Classification:** Advisory critique (warning)  
**Source:** `methodology/modeling-framework/validation/pim/rules/core.evl:62`

### Why this rule exists

The rule advises documenting why an element is manually maintained and how it should be reviewed. The gap is [PIM-TRACE-002] Manually maintained element ' ' has no rationale/review notes. Suggested fix: explain why this element is manual and how it should be reviewed. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.manuallyMaintained.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rationale.hasText() or self.reviewNotes.hasText()
```

This rule reads: `manuallyMaintained`, `rationale`, `reviewNotes`, `displayName`.

### Diagnostic and repair

> [PIM-TRACE-002] Manually maintained element ' ' has no rationale/review notes. Suggested fix: explain why this element is manual and how it should be reviewed.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
