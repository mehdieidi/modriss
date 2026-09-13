# CIM validation: Governance Readiness

Governance rules turn non-functional, security, privacy, compliance, risk, and readiness concerns into reviewable model facts. They keep a model from being declared ready while a known blocker has no owner, recommendation, evidence, or decision.

Source profile: `mde/validation/cim/rules/governance-readiness.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `NFRMustBeMeasurable`

**Context:** `CIM!NonFunctionalRequirement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:8`

### Why this rule exists

The rule checks whether nfr must be measurable. The non functional requirement element provides the relevant evidence through metric, target, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: NonFunctionalRequirement ' ' lacks metric or target.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.metric.hasText() and self.target.hasText()
```

This rule reads: `metric`, `target`, `labelText`.

### Diagnostic and repair

> Error [CIM-NFR-001] NonFunctionalRequirement ' ' lacks metric or target. Suggestion: define the measurable quality metric and target threshold.

**How to fix it:**

define the measurable quality metric and target threshold.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionBlockingNFRHasScenario`

**Context:** `CIM!NonFunctionalRequirement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:16`

### Why this rule exists

The rule checks whether production blocking nfr has scenario. It examines production blocking, scenarios, label text. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is Production-blocking NFR ' ' has no QualityScenario. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.productionBlocking = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.scenarios.notEmpty()
```

This rule reads: `productionBlocking`, `scenarios`, `labelText`.

### Diagnostic and repair

> Warning [CIM-NFR-003] Production-blocking NFR ' ' has no QualityScenario. Suggestion: add at least one scenario describing source, stimulus, environment, artifact, response, and responseMeasure.

**How to fix it:**

add at least one scenario describing source, stimulus, environment, artifact, response, and responseMeasure.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `QualityScenarioIsComplete`

**Context:** `CIM!QualityScenario`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:29`

### Why this rule exists

The rule checks whether quality scenario is complete. The quality scenario element provides the relevant evidence through source, stimulus, environment, artifact, response. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: QualityScenario ' ' is incomplete.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.source.hasText() and self.stimulus.hasText() and self.environment.hasText() and self.artifact.hasText() and self.response.hasText() and self.responseMeasure.hasText()
```

This rule reads: `source`, `stimulus`, `environment`, `artifact`, `response`, `responseMeasure`, `labelText`.

### Diagnostic and repair

> Error [CIM-NFR-004] QualityScenario ' ' is incomplete. Suggestion: fill source, stimulus, environment, artifact, response, and responseMeasure.

**How to fix it:**

fill source, stimulus, environment, artifact, response, and responseMeasure.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecurityConstraintMustConstrainBehaviorOrInformation`

**Context:** `CIM!SecurityConstraint`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:43`

### Why this rule exists

The rule checks whether security constraint must constrain behavior or information. The security constraint element provides the relevant evidence through constrained actors, constrained commands, constrained queries, constrained information, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: SecurityConstraint ' ' has no constrained actors, commands, queries, or information.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.constrainedActors.notEmpty() or self.constrainedCommands.notEmpty() or self.constrainedQueries.notEmpty() or self.constrainedInformation.notEmpty()
```

This rule reads: `constrainedActors`, `constrainedCommands`, `constrainedQueries`, `constrainedInformation`, `labelText`.

### Diagnostic and repair

> Error [CIM-SEC-001] SecurityConstraint ' ' has no constrained actors, commands, queries, or information. Suggestion: link the business elements affected by the security rule.

**How to fix it:**

link the business elements affected by the security rule.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecurityConstraintHasRuleOrThreatRationale`

**Context:** `CIM!SecurityConstraint`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:55`

### Why this rule exists

The rule checks whether security constraint has rule or threat rationale. The security constraint element provides the relevant evidence through authentication need, authorization rule, threat rationale, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: SecurityConstraint ' ' lacks authenticationNeed, authorizationRule, or threatRationale.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.authenticationNeed.hasText() or self.authorizationRule.hasText() or self.threatRationale.hasText()
```

This rule reads: `authenticationNeed`, `authorizationRule`, `threatRationale`, `labelText`.

### Diagnostic and repair

> Error [CIM-SEC-002] SecurityConstraint ' ' lacks authenticationNeed, authorizationRule, or threatRationale. Suggestion: state the security need/rule and why it exists.

**How to fix it:**

state the security need/rule and why it exists.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SecurityConstraintAlignsCommandAuthorization`

**Context:** `CIM!SecurityConstraint`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:64`

### Why this rule exists

The rule checks whether security constraint aligns command authorization. It examines authorization rule, constrained commands, authorization required, label text. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is SecurityConstraint ' ' defines an authorization rule but at least one constrained command does not. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.authorizationRule.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.constrainedCommands.forAll(commandItem | (commandItem.authorizationRequired = true) and commandItem.authorizationRule.hasText() )
```

This rule reads: `authorizationRule`, `constrainedCommands`, `authorizationRequired`, `labelText`.

### Diagnostic and repair

> Warning [CIM-SEC-003] SecurityConstraint ' ' defines an authorization rule but at least one constrained command does not. Suggestion: copy or specialize the authorization rule on each constrained command so command-level validation is explicit.

**How to fix it:**

copy or specialize the authorization rule on each constrained command so command-level validation is explicit.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SpecializedScopeIncludedInGenericScope`

**Context:** `CIM!SecurityConstraint`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:76`

### Why this rule exists

The rule checks whether specialized scope included in generic scope. It examines constrained commands, constrained elements, constrained queries, constrained actors, constrained information. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is SecurityConstraint ' ' uses specialized scope references that are not included in constrainedElements. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.constrainedCommands.forAll(commandItem | self.constrainedElements.includes(commandItem)) and self.constrainedQueries.forAll(queryItem | self.constrainedElements.includes(queryItem)) and self.constrainedActors.forAll(actorItem | self.constrainedElements.includes(actorItem)) and self.constrainedInformation.forAll(infoItem | self.constrainedElements.includes(infoItem))
```

This rule reads: `constrainedCommands`, `constrainedElements`, `constrainedQueries`, `constrainedActors`, `constrainedInformation`, `labelText`.

### Diagnostic and repair

> Warning [CIM-SEC-004] SecurityConstraint ' ' uses specialized scope references that are not included in constrainedElements. Suggestion: keep NonFunctionalRequirement.constrainedElements as the canonical scope and include every specialized target there.

**How to fix it:**

keep NonFunctionalRequirement.constrainedElements as the canonical scope and include every specialized target there.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PrivacyNeedsPurposeAndLegalBasis`

**Context:** `CIM!PrivacyConstraint`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:91`

### Why this rule exists

The rule checks whether privacy needs purpose and legal basis. The privacy constraint element provides the relevant evidence through data items, purpose, legal basis, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: PrivacyConstraint ' ' covers data items but lacks purpose or a known legalBasis.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.dataItems.isEmpty() or (self.purpose.hasText() and self.legalBasis.isDefined() and (self.legalBasis <> CIM!LegalBasis#UNKNOWN))
```

This rule reads: `dataItems`, `purpose`, `legalBasis`, `labelText`.

### Diagnostic and repair

> Error [CIM-PRIV-001] PrivacyConstraint ' ' covers data items but lacks purpose or a known legalBasis. Suggestion: provide the processing purpose and set legalBasis to CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTERESTS, PUBLIC_TASK, LEGITIMATE_INTEREST, or NOT_APPLICABLE.

**How to fix it:**

provide the processing purpose and set legalBasis to CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTERESTS, PUBLIC_TASK, LEGITIMATE_INTEREST, or NOT_APPLICABLE.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PrivacyConstraintHasRetentionOrMinimization`

**Context:** `CIM!PrivacyConstraint`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:101`

### Why this rule exists

The rule checks whether privacy constraint has retention or minimization. The privacy constraint element provides the relevant evidence through retention period, minimization rule, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: PrivacyConstraint ' ' lacks retentionPeriod and minimizationRule.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.retentionPeriod.hasText() or self.minimizationRule.hasText()
```

This rule reads: `retentionPeriod`, `minimizationRule`, `labelText`.

### Diagnostic and repair

> Error [CIM-PRIV-002] PrivacyConstraint ' ' lacks retentionPeriod and minimizationRule. Suggestion: document how long data is kept or how the collected data is minimized.

**How to fix it:**

document how long data is kept or how the collected data is minimized.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ConsentBasisRequiresConsent`

**Context:** `CIM!PrivacyConstraint`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:109`

### Why this rule exists

The rule checks whether consent basis requires consent. The privacy constraint element provides the relevant evidence through legal basis, consent required, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: PrivacyConstraint ' ' uses CONSENT legal basis but consentRequired is not true.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.legalBasis = CIM!LegalBasis#CONSENT
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.consentRequired = true
```

This rule reads: `legalBasis`, `consentRequired`, `labelText`.

### Diagnostic and repair

> Error [CIM-PRIV-003] PrivacyConstraint ' ' uses CONSENT legal basis but consentRequired is not true. Suggestion: set consentRequired=true or choose the correct legalBasis.

**How to fix it:**

set consentRequired=true or choose the correct legalBasis.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DataResidencyHasAllowedArea`

**Context:** `CIM!PrivacyConstraint`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:118`

### Why this rule exists

The rule checks whether data residency has allowed area. It examines data residency required, allowed residency area, label text. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is PrivacyConstraint ' ' requires data residency but has no allowedResidencyArea. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.dataResidencyRequired = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.allowedResidencyArea.hasText()
```

This rule reads: `dataResidencyRequired`, `allowedResidencyArea`, `labelText`.

### Diagnostic and repair

> Warning [CIM-PRIV-004] PrivacyConstraint ' ' requires data residency but has no allowedResidencyArea. Suggestion: specify the allowed region, country, jurisdiction, or business residency boundary.

**How to fix it:**

specify the allowed region, country, jurisdiction, or business residency boundary.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ComplianceConstraintIsAuditable`

**Context:** `CIM!ComplianceConstraint`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:131`

### Why this rule exists

The rule checks whether compliance constraint is auditable. The compliance constraint element provides the relevant evidence through regulation, control id, control objective, evidence required, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: ComplianceConstraint ' ' lacks regulation, controlId, controlObjective, or evidenceRequired.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.regulation.hasText() and self.controlId.hasText() and self.controlObjective.hasText() and self.evidenceRequired.hasText()
```

This rule reads: `regulation`, `controlId`, `controlObjective`, `evidenceRequired`, `labelText`.

### Diagnostic and repair

> Error [CIM-COMP-001] ComplianceConstraint ' ' lacks regulation, controlId, controlObjective, or evidenceRequired. Suggestion: identify the regulation/control and the evidence needed for audit or review.

**How to fix it:**

identify the regulation/control and the evidence needed for audit or review.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ComplianceConstraintHasScope`

**Context:** `CIM!ComplianceConstraint`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:141`

### Why this rule exists

The rule checks whether compliance constraint has scope. It examines scoped elements, label text. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is ComplianceConstraint ' ' has no scopedElements. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.scopedElements.notEmpty()
```

This rule reads: `scopedElements`, `labelText`.

### Diagnostic and repair

> Warning [CIM-COMP-002] ComplianceConstraint ' ' has no scopedElements. Suggestion: link the elements that must satisfy this compliance control.

**How to fix it:**

link the elements that must satisfy this compliance control.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RiskIsActionableWhenBlocking`

**Context:** `CIM!Risk`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:153`

### Why this rule exists

The rule checks whether risk is actionable when blocking. The risk element provides the relevant evidence through production blocking, risk statement, probability, impact, mitigation. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Production-blocking Risk ' ' is not actionable.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.productionBlocking = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.riskStatement.hasText() and self.probability.hasText() and self.impact.hasText() and self.mitigation.hasText() and self.affectedElements.notEmpty()
```

This rule reads: `productionBlocking`, `riskStatement`, `probability`, `impact`, `mitigation`, `affectedElements`, `labelText`.

### Diagnostic and repair

> Error [CIM-RISK-001] Production-blocking Risk ' ' is not actionable. Suggestion: provide riskStatement, probability, impact, mitigation, and affectedElements before it can be managed.

**How to fix it:**

provide riskStatement, probability, impact, mitigation, and affectedElements before it can be managed.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RiskHasAffectedElements`

**Context:** `CIM!Risk`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:165`

### Why this rule exists

The rule checks whether risk has affected elements. It examines affected elements, label text. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is Risk ' ' has no affectedElements. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.affectedElements.notEmpty()
```

This rule reads: `affectedElements`, `labelText`.

### Diagnostic and repair

> Warning [CIM-RISK-002] Risk ' ' has no affectedElements. Suggestion: link the goals, capabilities, processes, commands, information, or external systems affected by the risk.

**How to fix it:**

link the goals, capabilities, processes, commands, information, or external systems affected by the risk.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `AssumptionHasStatementAndValidation`

**Context:** `CIM!Assumption`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:177`

### Why this rule exists

The rule checks whether assumption has statement and validation. The assumption element provides the relevant evidence through assumption statement, validation approach, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Assumption ' ' lacks assumptionStatement or validationApproach.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.assumptionStatement.hasText() and self.validationApproach.hasText()
```

This rule reads: `assumptionStatement`, `validationApproach`, `labelText`.

### Diagnostic and repair

> Error [CIM-ASM-001] Assumption ' ' lacks assumptionStatement or validationApproach. Suggestion: state what is assumed and how it will be validated or retired.

**How to fix it:**

state what is assumed and how it will be validated or retired.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AcceptedAssumptionHasDecisionDetails`

**Context:** `CIM!Assumption`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:185`

### Why this rule exists

The rule checks whether accepted assumption has decision details. The assumption element provides the relevant evidence through accepted, accepted by, accepted on, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Assumption ' ' is accepted but lacks acceptedBy or acceptedOn.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.accepted = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.acceptedBy.hasText() and self.acceptedOn.isDefined()
```

This rule reads: `accepted`, `acceptedBy`, `acceptedOn`, `labelText`.

### Diagnostic and repair

> Error [CIM-ASM-002] Assumption ' ' is accepted but lacks acceptedBy or acceptedOn. Suggestion: record who accepted the assumption and when.

**How to fix it:**

record who accepted the assumption and when.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BlockingHotspotRequiresOwner`

**Context:** `CIM!Hotspot`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:198`

### Why this rule exists

The rule checks whether blocking hotspot requires owner. The hotspot element provides the relevant evidence through blocks transformation, blocks production, owner, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Blocking Hotspot ' ' has no owner.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.blocksTransformation = true) or (self.blocksProduction = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.owner.hasText()
```

This rule reads: `blocksTransformation`, `blocksProduction`, `owner`, `labelText`.

### Diagnostic and repair

> Error [CIM-HOT-001] Blocking Hotspot ' ' has no owner. Suggestion: assign the person/role responsible for resolving the open question.

**How to fix it:**

assign the person/role responsible for resolving the open question.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BlockingHotspotHasDueDateAndImpact`

**Context:** `CIM!Hotspot`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:207`

### Why this rule exists

The rule checks whether blocking hotspot has due date and impact. It examines blocks transformation, blocks production, due date, impact, attached to. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is Blocking Hotspot ' ' lacks dueDate, impact, or attachedTo links. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.blocksTransformation = true) or (self.blocksProduction = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.dueDate.isDefined() and self.impact.hasText() and self.attachedTo.notEmpty()
```

This rule reads: `blocksTransformation`, `blocksProduction`, `dueDate`, `impact`, `attachedTo`, `labelText`.

### Diagnostic and repair

> Warning [CIM-HOT-002] Blocking Hotspot ' ' lacks dueDate, impact, or attachedTo links. Suggestion: add a target resolution date, business impact, and affected elements.

**How to fix it:**

add a target resolution date, business impact, and affected elements.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ServiceGranularityPreferenceHasRationale`

**Context:** `CIM!TransformationProfile`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:220`

### Why this rule exists

The rule checks whether service granularity preference has rationale. It examines prefer capability as service boundary, default service granularity rationale. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is TransformationProfile prefers capabilities as service boundaries but has no defaultServiceGranularityRationale. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.preferCapabilityAsServiceBoundary = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.defaultServiceGranularityRationale.hasText()
```

This rule reads: `preferCapabilityAsServiceBoundary`, `defaultServiceGranularityRationale`.

### Diagnostic and repair

> Warning [CIM-TP-001] TransformationProfile prefers capabilities as service boundaries but has no defaultServiceGranularityRationale. Suggestion: document why capability-based service boundaries are preferred and when exceptions are allowed.

**How to fix it:**

document why capability-based service boundaries are preferred and when exceptions are allowed.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RequiredManualDecisionsAreOwned`

**Context:** `CIM!TransformationProfile`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:229`

### Why this rule exists

The rule checks whether required manual decisions are owned. It examines required decisions, question, decision owner. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is TransformationProfile has requiredDecisions without question or decisionOwner. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.requiredDecisions.forAll(decisionItem | decisionItem.question.hasText() and decisionItem.decisionOwner.hasText() )
```

This rule reads: `requiredDecisions`, `question`, `decisionOwner`.

### Diagnostic and repair

> Warning [CIM-TP-002] TransformationProfile has requiredDecisions without question or decisionOwner. Suggestion: make each manual decision explicit and assign an owner.

**How to fix it:**

make each manual decision explicit and assign an owner.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionReadyAssessmentIsConsistent`

**Context:** `KERNEL!ProductionReadinessAssessment`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:245`

### Why this rule exists

The rule checks whether production ready assessment is consistent. The production readiness assessment element provides the relevant evidence through production ready, readiness status, checks, passed, findings. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: ProductionReadinessAssessment is marked productionReady but readinessStatus/checks/findings are inconsistent.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.productionReady = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.readinessStatus = KERNEL!LifecycleStatus#PRODUCTION_READY) and self.checks.forAll(checkItem | checkItem.passed = true) and self.findings.forAll(findingItem | not (findingItem.blocking = true))
```

This rule reads: `productionReady`, `readinessStatus`, `checks`, `passed`, `findings`, `blocking`.

### Diagnostic and repair

> Error [CIM-READY-001] ProductionReadinessAssessment is marked productionReady but readinessStatus/checks/findings are inconsistent. Suggestion: set readinessStatus=PRODUCTION_READY only when all checks pass and no blocking findings remain.

**How to fix it:**

set readinessStatus=PRODUCTION_READY only when all checks pass and no blocking findings remain.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ReadinessAssessmentHasAssessor`

**Context:** `KERNEL!ProductionReadinessAssessment`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:257`

### Why this rule exists

The rule checks whether readiness assessment has assessor. It examines assessed at, assessed by. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is ProductionReadinessAssessment lacks assessedAt or assessedBy. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.assessedAt.isDefined() and self.assessedBy.hasText()
```

This rule reads: `assessedAt`, `assessedBy`.

### Diagnostic and repair

> Warning [CIM-READY-002] ProductionReadinessAssessment lacks assessedAt or assessedBy. Suggestion: record when the assessment happened and who performed it.

**How to fix it:**

record when the assessment happened and who performed it.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `BlockingFindingHasRecommendation`

**Context:** `KERNEL!ReadinessFinding`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:269`

### Why this rule exists

The rule checks whether blocking finding has recommendation. The readiness finding element provides the relevant evidence through blocking, message, recommendation, affected elements, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Blocking ReadinessFinding ' ' lacks message, recommendation, or affectedElements.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.blocking = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.`message`.hasText() and self.recommendation.hasText() and self.affectedElements.notEmpty()
```

This rule reads: `blocking`, `message`, `recommendation`, `affectedElements`, `labelText`.

### Diagnostic and repair

> Error [CIM-READY-003] Blocking ReadinessFinding ' ' lacks message, recommendation, or affectedElements. Suggestion: make the finding actionable by explaining the issue, the remediation, and the affected elements.

**How to fix it:**

make the finding actionable by explaining the issue, the remediation, and the affected elements.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `FailedReadinessCheckHasRemediation`

**Context:** `KERNEL!ReadinessCheck`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:283`

### Why this rule exists

The rule checks whether failed readiness check has remediation. The readiness check element provides the relevant evidence through passed, message, remediation, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Failed ReadinessCheck ' ' lacks message or remediation.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : not (self.passed = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.`message`.hasText() and self.remediation.hasText()
```

This rule reads: `passed`, `message`, `remediation`, `labelText`.

### Diagnostic and repair

> Error [CIM-READY-004] Failed ReadinessCheck ' ' lacks message or remediation. Suggestion: explain why the check failed and how the user can make it pass.

**How to fix it:**

explain why the check failed and how the user can make it pass.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BlockingManualDecisionHasOwner`

**Context:** `KERNEL!ManualDecision`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:296`

### Why this rule exists

The rule checks whether blocking manual decision has owner. The manual decision element provides the relevant evidence through blocking, question, decision owner, label text. At this level, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Blocking ManualDecision ' ' lacks question or decisionOwner.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.blocking = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.question.hasText() and self.decisionOwner.hasText()
```

This rule reads: `blocking`, `question`, `decisionOwner`, `labelText`.

### Diagnostic and repair

> Error [CIM-READY-005] Blocking ManualDecision ' ' lacks question or decisionOwner. Suggestion: write the decision question and assign an accountable owner.

**How to fix it:**

write the decision question and assign an accountable owner.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ManualDecisionHasDueDate`

**Context:** `KERNEL!ManualDecision`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/governance-readiness.evl:305`

### Why this rule exists

The rule checks whether manual decision has due date. It examines blocking, due date, label text. Within this part of the model, security, privacy, compliance, risk, and readiness decisions are still visible when architecture work begins. The gap is Blocking ManualDecision ' ' has no dueDate. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.blocking = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.dueDate.isDefined()
```

This rule reads: `blocking`, `dueDate`, `labelText`.

### Diagnostic and repair

> Warning [CIM-READY-006] Blocking ManualDecision ' ' has no dueDate. Suggestion: set a due date to avoid indefinite transformation or production blockage.

**How to fix it:**

set a due date to avoid indefinite transformation or production blockage.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
