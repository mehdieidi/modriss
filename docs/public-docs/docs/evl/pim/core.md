# PIM validation: Core

PIM core rules protect the provider-independent architecture's identity, portability, naming, traceability, and explanation. They are the guard against a PIM that is structurally valid but already assumes an AWS resource or has lost its business origin.

Source profile: `mde/validation/pim/rules/core.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `DomainNameRequired`

**Context:** `PIM!PIMModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/core.evl:8`

### Why this rule exists

Checks that domain name required. The pimmodel element owns the evidence for this decision, including domain name. At this level, the PIM remains portable and traceable instead of quietly becoming an AWS design under a generic name; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-ROOT-002] The PIM model is missing a domainName.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

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
**Source:** `mde/validation/pim/rules/core.evl:14`

### Why this rule exists

Checks that implementation profile required for generation. The pimmodel element owns the evidence for this decision, including implementation profile. At this level, the PIM remains portable and traceable instead of quietly becoming an AWS design under a generic name; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-ROOT-003] Artifact generation requires an ImplementationProfile.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

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

## `NoProviderSpecificNamesInModel`

**Context:** `PIM!PIMModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/core.evl:20`

### Why this rule exists

CIM/PIM are intentionally portable. This rule stops AWS service names from entering provider-neutral vocabulary where they would bias the architecture and make a later provider choice look inevitable.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : KERNEL!ModelElement.all.forAll(elementItem | not elementItem.name.containsProviderToken() and not elementItem.summary.containsProviderToken() and not elementItem.description.containsProviderToken() and not elementItem.documentation.containsProviderToken() )
```

This rule reads: `name`, `summary`, `description`, `documentation`.

### Diagnostic and repair

> [PIM-ROOT-004] The model contains provider-specific wording in a name or description. Fix: replace cloud/vendor service names with provider-independent domain terms such as function, queue, topic, object store, identity provider, event bus, trace, metric or deployment unit.

**How to fix it:**

replace cloud/vendor service names with provider-independent domain terms such as function, queue, topic, object store, identity provider, event bus, trace, metric or deployment unit.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DefaultCorrelationIdShouldBeNamed`

**Context:** `PIM!PIMModel`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/core.evl:30`

### Why this rule exists

This rule checks a semantic decision. This is a review signal about default correlation id name, not a cosmetic naming preference. In this part of the model, the PIM remains portable and traceable instead of quietly becoming an AWS design under a generic name; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: No defaultCorrelationIdName is defined. Suggested fix: set a portable name such as correlationId so APIs, events, workflows and logs can share the same correlation convention. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

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
**Source:** `mde/validation/pim/rules/core.evl:35`

### Why this rule exists

This rule checks a semantic decision. This is a review signal about architecture style, all service apis, event types, all service channels, all service workflows, not a cosmetic naming preference. In this part of the model, the PIM remains portable and traceable instead of quietly becoming an AWS design under a generic name; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: The selected architectureStyle does not match the model contents. Suggested fix: add the matching APIs/events/workflows or choose HYBRID_SERVERLESS if multiple styles are intentionally combined. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

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
**Source:** `mde/validation/pim/rules/core.evl:48`

### Why this rule exists

The PIM model is a transformation boundary, so an element without an ID cannot be reliably matched with its CIM origin or with a later generated revision. This rule protects synchronization, not cosmetic naming.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

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

## `PortableNameRecommended`

**Context:** `KERNEL!ModelElement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/core.evl:54`

### Why this rule exists

Advises that portable name recommended. This is a review signal about is kind of, name, display name, not a cosmetic naming preference. In this part of the model, the PIM remains portable and traceable instead of quietly becoming an AWS design under a generic name; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-NAME-002] The element name ' ' is hard to use in generated artifacts. Suggested fix: start with a letter and use only letters, numbers, spaces, underscores, hyphens or dots. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : not self.isKindOf(KERNEL!TraceLink) and not self.isKindOf(KERNEL!ManualDecision) and not self.isKindOf(KERNEL!ReadinessFinding) and not self.isKindOf(KERNEL!ReadinessCheck)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.name.hasText() and self.name.matches("^[A-Za-z][A-Za-z0-9 _\\-.]*$")
```

This rule reads: `isKindOf`, `name`, `displayName`.

### Diagnostic and repair

> [PIM-NAME-002] The element name ' ' is hard to use in generated artifacts. Suggested fix: start with a letter and use only letters, numbers, spaces, underscores, hyphens or dots.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `GeneratedElementShouldBeTraceable`

**Context:** `KERNEL!TraceableElement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/core.evl:68`

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
**Source:** `mde/validation/pim/rules/core.evl:82`

### Why this rule exists

Advises that rationale recommended for manually maintained elements. This is a review signal about manually maintained, rationale, review notes, display name, not a cosmetic naming preference. In this part of the model, the PIM remains portable and traceable instead of quietly becoming an AWS design under a generic name; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-TRACE-002] Manually maintained element ' ' has no rationale/review notes. Suggested fix: explain why this element is manual and how it should be reviewed. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

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
