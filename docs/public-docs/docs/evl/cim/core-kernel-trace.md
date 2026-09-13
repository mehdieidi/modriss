# CIM validation: Core Kernel Trace

These rules make a CIM model a coherent starting point for refinement. They require a semantic core, protect model identity and origin, and stop provider vocabulary from leaking into the computation-independent language.

Source profile: `mde/validation/cim/rules/core-kernel-trace.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `CIMModelHasSemanticCore`

**Context:** `CIM!CIMModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:8`

### Why this rule exists

The root must contain enough business scope, organization, and intent to make its descendants interpretable. A collection of otherwise valid classes is not a CIM unless its problem boundary is named.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.goals.notEmpty() and self.actors.notEmpty() and self.capabilities.notEmpty()
```

This rule reads: `goals`, `actors`, `capabilities`.

### Diagnostic and repair

> Error [CIM-CORE-001] The CIM model must contain at least one business goal, one actor, and one business capability. Suggestion: add the minimal business core before semantic validation: a goal that explains value, an actor that initiates or observes work, and a capability that realizes the goal.

**How to fix it:**

add the minimal business core before semantic validation: a goal that explains value, an actor that initiates or observes work, and a capability that realizes the goal.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CIMModelDeclaresBusinessScope`

**Context:** `CIM!CIMModel`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:19`

### Why this rule exists

The rule checks whether cim model declares business scope. It examines domain name, business scope, organization name. Within this part of the model, the CIM remains a portable, explainable source model rather than a loose collection of nouns. The gap is The CIM model is missing domainName, businessScope, or organizationName. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.domainName.hasText() and self.businessScope.hasText() and self.organizationName.hasText()
```

This rule reads: `domainName`, `businessScope`, `organizationName`.

### Diagnostic and repair

> Warning [CIM-CORE-002] The CIM model is missing domainName, businessScope, or organizationName. Suggestion: describe the business domain, the boundary of what is included/excluded, and the organization or business unit that owns the model.

**How to fix it:**

describe the business domain, the boundary of what is included/excluded, and the organization or business unit that owns the model.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionReadyModelHasNoBlockers`

**Context:** `CIM!CIMModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:28`

### Why this rule exists

Production readiness is a decision supported by evidence, not a label that overrides open blockers. This rule prevents a readiness flag from contradicting the assessment's own findings.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionReadyIntent()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.hotspots.forAll(hotspotItem | not (hotspotItem.blocksProduction = true))) and (self.risks.forAll(riskItem | not (riskItem.productionBlocking = true))) and ((self.readiness.isUndefined()) or (self.readiness.findings.forAll(findingItem | not (findingItem.blocking = true))) and (self.readiness.checks.forAll(checkItem | checkItem.passed = true)))
```

This rule reads: `isProductionReadyIntent`, `hotspots`, `blocksProduction`, `risks`, `productionBlocking`, `readiness`, `blocking`, `passed`.

### Diagnostic and repair

> Error [CIM-CORE-003] This model is marked production-ready while blocking hotspots, production-blocking risks, blocking readiness findings, or failed readiness checks still exist. Suggestion: resolve or downgrade all blockers, record mitigations, and update readiness checks before setting lifecycleStatus/readiness to production-ready.

**How to fix it:**

resolve or downgrade all blockers, record mitigations, and update readiness checks before setting lifecycleStatus/readiness to production-ready.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TransformationProfileExplicitAuthIsEnforced`

**Context:** `CIM!CIMModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:42`

### Why this rule exists

The rule checks whether transformation profile explicit auth is enforced. The cimmodel element provides the relevant evidence through transformation profile, commands, has human issuer, has authorization decision. At this level, the CIM remains a portable, explainable source model rather than a loose collection of nouns. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: The transformation profile requires explicit actor authorization, but at least one human-issued command lacks authorizationRequired=true and a business authorizationRule.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.transformationProfile.isDefined() and (self.transformationProfile.requireExplicitActorAuthForCommands = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.commands.forAll(commandItem | (not commandItem.hasHumanIssuer()) or commandItem.hasAuthorizationDecision() )
```

This rule reads: `transformationProfile`, `commands`, `hasHumanIssuer`, `hasAuthorizationDecision`.

### Diagnostic and repair

> Error [CIM-CORE-004] The transformation profile requires explicit actor authorization, but at least one human-issued command lacks authorizationRequired=true and a business authorizationRule. Suggestion: for each human-issued command, either define the authorization rule or remove the human actor as an issuer if the command is internal/system-triggered.

**How to fix it:**

for each human-issued command, either define the authorization rule or remove the human actor as an issuer if the command is internal/system-triggered.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TransformationProfilePrivacyClassificationIsEnforced`

**Context:** `CIM!CIMModel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:56`

### Why this rule exists

The rule checks whether transformation profile privacy classification is enforced. The cimmodel element provides the relevant evidence through transformation profile, information items. At this level, the CIM remains a portable, explainable source model rather than a loose collection of nouns. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: The transformation profile requires privacy classification for all information items, but at least one item has no DataClassification.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.transformationProfile.isDefined() and (self.transformationProfile.requirePrivacyClassificationForAllInformation = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.informationItems.forAll(item | item.classification.isDefined())
```

This rule reads: `transformationProfile`, `informationItems`.

### Diagnostic and repair

> Error [CIM-CORE-005] The transformation profile requires privacy classification for all information items, but at least one item has no DataClassification. Suggestion: classify every InformationItem as PUBLIC, INTERNAL, CONFIDENTIAL, PERSONAL, SENSITIVE_PERSONAL, FINANCIAL, HEALTH, AUTHENTICATION_SECRET, or REGULATED, and add privacy constraints where required.

**How to fix it:**

classify every InformationItem as PUBLIC, INTERNAL, CONFIDENTIAL, PERSONAL, SENSITIVE_PERSONAL, FINANCIAL, HEALTH, AUTHENTICATION_SECRET, or REGULATED, and add privacy constraints where required.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ModelElementNameIsPresent`

**Context:** `KERNEL!ModelElement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:72`

### Why this rule exists

The rule checks whether model element name is present. The model element element provides the relevant evidence through name. At this level, the CIM remains a portable, explainable source model rather than a loose collection of nouns. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: A model element has no user-facing name.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.name.hasText()
```

This rule reads: `name`.

### Diagnostic and repair

> Error [CIM-KERNEL-001] A model element has no user-facing name. Suggestion: provide a concise business name for this element. Avoid implementation names, provider products, acronyms without definitions, or generated placeholders.

**How to fix it:**

provide a concise business name for this element. Avoid implementation names, provider products, acronyms without definitions, or generated placeholders.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ModelElementAvoidsProviderSpecificTerms`

**Context:** `KERNEL!ModelElement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:80`

### Why this rule exists

The rule checks whether model element avoids provider specific terms. The model element element provides the relevant evidence through contains provider technology term, label text. At this level, the CIM remains a portable, explainable source model rather than a loose collection of nouns. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: ' ' contains provider-specific or implementation-level terms.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.containsProviderTechnologyTerm()
```

This rule reads: `containsProviderTechnologyTerm`, `labelText`.

### Diagnostic and repair

> Error [CIM-KERNEL-002] ' ' contains provider-specific or implementation-level terms. Suggestion: rename/reword it in CIM-level business language. For example, prefer 'Customer notification requested' over 'SNS message sent' and 'Document storage' over 'S3 bucket'.

**How to fix it:**

rename/reword it in CIM-level business language. For example, prefer 'Customer notification requested' over 'SNS message sent' and 'Document storage' over 'S3 bucket'.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ModelElementHasExplanation`

**Context:** `KERNEL!ModelElement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:88`

### Why this rule exists

The rule checks whether model element has explanation. It examines summary, description, documentation, label text. Within this part of the model, the CIM remains a portable, explainable source model rather than a loose collection of nouns. The gap is ' ' has a name but no explanatory summary, description, or documentation. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.summary.hasText() or self.description.hasText() or self.documentation.hasText()
```

This rule reads: `summary`, `description`, `documentation`, `labelText`.

### Diagnostic and repair

> Warning [CIM-KERNEL-003] ' ' has a name but no explanatory summary, description, or documentation. Suggestion: add a short explanation of its business meaning so reviewers and transformations do not infer semantics from the name alone.

**How to fix it:**

add a short explanation of its business meaning so reviewers and transformations do not infer semantics from the name alone.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `TraceableElementHasSourceOrRationale`

**Context:** `KERNEL!TraceableElement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:101`

### Why this rule exists

The rule checks whether traceable element has source or rationale. It examines source reference, source excerpt, source uri, rationale, label text. Within this part of the model, the CIM remains a portable, explainable source model rather than a loose collection of nouns. The gap is ' ' has no source reference or rationale. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.sourceReference.hasText() or self.sourceExcerpt.hasText() or self.sourceUri.hasText() or self.rationale.hasText()
```

This rule reads: `sourceReference`, `sourceExcerpt`, `sourceUri`, `rationale`, `labelText`.

### Diagnostic and repair

> Warning [CIM-TRACE-001] ' ' has no source reference or rationale. Suggestion: link the element to the workshop note, requirement, policy, regulation, interview, or decision record that justifies it.

**How to fix it:**

link the element to the workshop note, requirement, policy, regulation, interview, or decision record that justifies it.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `GeneratedElementHasOrigin`

**Context:** `KERNEL!TraceableElement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:111`

### Why this rule exists

The origin fields are the evidence that a generated element came from a known upstream decision. Without them, regeneration and manual review cannot distinguish intended refinement from accidental residue.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.generatedByTransformation = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.generatedFrom.hasText() or self.traceId.hasText() or self.sourceReference.hasText()
```

This rule reads: `generatedByTransformation`, `generatedFrom`, `traceId`, `sourceReference`, `labelText`.

### Diagnostic and repair

> Error [CIM-TRACE-002] ' ' is marked generatedByTransformation=true but has no generatedFrom, traceId, or sourceReference. Suggestion: record the transformation rule, source element, or trace identifier that generated it.

**How to fix it:**

record the transformation rule, source element, or trace identifier that generated it.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TraceLinkHasReferenceOrExternalId`

**Context:** `KERNEL!TraceLink`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:124`

### Why this rule exists

The rule checks whether trace link has reference or external id. The trace link element provides the relevant evidence through source, target, source element id, target element id. At this level, the CIM remains a portable, explainable source model rather than a loose collection of nouns. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: A TraceLink must identify both ends either by model references or by sourceElementId/targetElementId.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : ((self.source.isDefined()) and (self.target.isDefined())) or (self.sourceElementId.hasText() and self.targetElementId.hasText())
```

This rule reads: `source`, `target`, `sourceElementId`, `targetElementId`.

### Diagnostic and repair

> Error [CIM-TRACE-003] A TraceLink must identify both ends either by model references or by sourceElementId/targetElementId. Suggestion: set source and target references when both elements are in the model; otherwise provide stable external IDs for both ends.

**How to fix it:**

set source and target references when both elements are in the model; otherwise provide stable external IDs for both ends.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TraceLinkDoesNotPointToSameElement`

**Context:** `KERNEL!TraceLink`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/core-kernel-trace.evl:134`

### Why this rule exists

The rule checks whether trace link does not point to same element. The trace link element provides the relevant evidence through source, target, label text. At this level, the CIM remains a portable, explainable source model rather than a loose collection of nouns. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: TraceLink ' ' points from an element to itself.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.source.isUndefined()) or (self.target.isUndefined()) or (self.source <> self.target)
```

This rule reads: `source`, `target`, `labelText`.

### Diagnostic and repair

> Error [CIM-TRACE-004] TraceLink ' ' points from an element to itself. Suggestion: remove the self-link or change it to connect two distinct elements with a meaningful TraceLinkType.

**How to fix it:**

remove the self-link or change it to connect two distinct elements with a meaningful TraceLinkType.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
