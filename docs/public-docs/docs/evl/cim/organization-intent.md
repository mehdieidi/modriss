# CIM validation — Organization Intent

Organization and intent rules ask whether the business problem is specific enough to guide architecture. Requirements need observable fit, goals need measurable success, actors and systems need explicit trust expectations, and capabilities need ownership and behavior.

Source profile: `mde/validation/cim/rules/organization-intent.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `RequirementHasFitCriterionOrAcceptanceCriteria`

**Context:** `CIM!Requirement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:8`

### Why this rule exists

Checks that requirement has fit criterion or acceptance criteria. The requirement element owns the evidence for this decision, including fit criterion, acceptance criteria, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Requirement ' ' has neither a fitCriterion nor acceptanceCriteria.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.fitCriterion.hasText() or self.acceptanceCriteria.notEmpty()
```

This rule reads: `fitCriterion`, `acceptanceCriteria`, `labelText`.

### Diagnostic and repair

> Error [CIM-REQ-001] Requirement ' ' has neither a fitCriterion nor acceptanceCriteria. Suggestion: add a measurable fit criterion or at least one Given/When/Then-style acceptance criterion describing how satisfaction will be judged.

**How to fix it:**

add a measurable fit criterion or at least one Given/When/Then-style acceptance criterion describing how satisfaction will be judged.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionBlockingRequirementIsMandatory`

**Context:** `CIM!Requirement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:16`

### Why this rule exists

Checks that production blocking requirement is mandatory. The requirement element owns the evidence for this decision, including production blocking, mandatory, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Requirement ' ' blocks production but is not marked mandatory.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.productionBlocking = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.mandatory = true
```

This rule reads: `productionBlocking`, `mandatory`, `labelText`.

### Diagnostic and repair

> Error [CIM-REQ-002] Requirement ' ' blocks production but is not marked mandatory. Suggestion: set mandatory=true or downgrade productionBlocking=false if the requirement is not a release gate.

**How to fix it:**

set mandatory=true or downgrade productionBlocking=false if the requirement is not a release gate.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RequirementDoesNotDependOnItself`

**Context:** `CIM!Requirement`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:25`

### Why this rule exists

A self-dependency cannot explain delivery order or refinement; it is usually an accidental reference or a broken import. Rejecting it early prevents circular planning logic.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (not self.dependsOn.includes(self)) and (not self.conflictsWith.includes(self))
```

This rule reads: `dependsOn`, `conflictsWith`, `labelText`.

### Diagnostic and repair

> Error [CIM-REQ-003] Requirement ' ' depends on or conflicts with itself. Suggestion: remove the self-reference and model the actual dependency/conflict with another requirement.

**How to fix it:**

remove the self-reference and model the actual dependency/conflict with another requirement.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RequirementSupportsGoalOrConstrainsElement`

**Context:** `CIM!Requirement`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:34`

### Why this rule exists

Advises that requirement supports goal or constrains element. This is a review signal about supports goals, constrains, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Requirement ' ' is not connected to a business goal or constrained element. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.supportsGoals.notEmpty() or self.constrains.notEmpty()
```

This rule reads: `supportsGoals`, `constrains`, `labelText`.

### Diagnostic and repair

> Warning [CIM-REQ-004] Requirement ' ' is not connected to a business goal or constrained element. Suggestion: link it to the goal it supports or the model element it constrains so transformation and readiness checks can assess impact.

**How to fix it:**

link it to the goal it supports or the model element it constrains so transformation and readiness checks can assess impact.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `AcceptanceCriterionUsesGivenWhenThen`

**Context:** `CIM!AcceptanceCriterion`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:46`

### Why this rule exists

Given/When/Then separates precondition, stimulus, and observable outcome. The rule makes acceptance criteria testable and keeps them meaningful after the model is transformed into implementation work.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.givenContext.hasText() and self.whenAction.hasText() and self.thenOutcome.hasText()
```

This rule reads: `givenContext`, `whenAction`, `thenOutcome`, `labelText`.

### Diagnostic and repair

> Error [CIM-REQ-005] AcceptanceCriterion ' ' is incomplete. Suggestion: fill givenContext, whenAction, and thenOutcome so the condition, stimulus, and expected result are explicit.

**How to fix it:**

fill givenContext, whenAction, and thenOutcome so the condition, stimulus, and expected result are explicit.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AutomatableAcceptanceCriterionHasTarget`

**Context:** `CIM!AcceptanceCriterion`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:55`

### Why this rule exists

Advises that automatable acceptance criterion has target. This is a review signal about automatable test candidate, measurable target, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: AcceptanceCriterion ' ' is marked automatable but has no measurableTarget. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.automatableTestCandidate = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.measurableTarget.hasText()
```

This rule reads: `automatableTestCandidate`, `measurableTarget`, `labelText`.

### Diagnostic and repair

> Warning [CIM-REQ-006] AcceptanceCriterion ' ' is marked automatable but has no measurableTarget. Suggestion: add a concrete observable target such as a threshold, state change, emitted event, or response condition.

**How to fix it:**

add a concrete observable target such as a threshold, state change, emitted event, or response condition.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `BusinessGoalHasSuccessCriterion`

**Context:** `CIM!BusinessGoal`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:68`

### Why this rule exists

Checks that business goal has success criterion. The business goal element owns the evidence for this decision, including success criterion, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessGoal ' ' has no successCriterion.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.successCriterion.hasText()
```

This rule reads: `successCriterion`, `labelText`.

### Diagnostic and repair

> Error [CIM-GOAL-001] BusinessGoal ' ' has no successCriterion. Suggestion: state the business outcome that proves the goal has been achieved, preferably in measurable terms.

**How to fix it:**

state the business outcome that proves the goal has been achieved, preferably in measurable terms.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CriticalGoalHasOwnerAndKpi`

**Context:** `CIM!BusinessGoal`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:76`

### Why this rule exists

Advises that critical goal has owner and kpi. This is a review signal about priority, owners, measured by, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Critical BusinessGoal ' ' has no owner or KPI. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.priority = KERNEL!Priority#CRITICAL
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.owners.notEmpty() and self.measuredBy.notEmpty()
```

This rule reads: `priority`, `owners`, `measuredBy`, `labelText`.

### Diagnostic and repair

> Warning [CIM-GOAL-002] Critical BusinessGoal ' ' has no owner or KPI. Suggestion: assign an accountable stakeholder and at least one KPI so the critical goal can be governed.

**How to fix it:**

assign an accountable stakeholder and at least one KPI so the critical goal can be governed.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `BusinessGoalExplainsValueAndRisk`

**Context:** `CIM!BusinessGoal`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:85`

### Why this rule exists

Advises that business goal explains value and risk. This is a review signal about business value, failure consequence, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: BusinessGoal ' ' does not explain businessValue or failureConsequence. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.businessValue.hasText() and self.failureConsequence.hasText()
```

This rule reads: `businessValue`, `failureConsequence`, `labelText`.

### Diagnostic and repair

> Warning [CIM-GOAL-003] BusinessGoal ' ' does not explain businessValue or failureConsequence. Suggestion: describe why the goal matters and what happens if it is not met.

**How to fix it:**

describe why the goal matters and what happens if it is not met.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `KPIIsMeasurable`

**Context:** `CIM!KPI`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:97`

### Why this rule exists

A KPI is useful only when it names what is measured, how it is compared, and what target/unit it uses. The rule prevents aspirational prose from being mistaken for an operational measure.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.metricName.hasText() and self.targetValue.hasText() and self.unit.hasText()
```

This rule reads: `metricName`, `targetValue`, `unit`, `labelText`.

### Diagnostic and repair

> Error [CIM-KPI-001] KPI ' ' is not measurable. Suggestion: provide metricName, targetValue, and unit, for example 'approval latency', '<= 2', and 'business days'.

**How to fix it:**

provide metricName, targetValue, and unit, for example 'approval latency', '<= 2', and 'business days'.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `KPIHasMeasurementMethod`

**Context:** `CIM!KPI`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:106`

### Why this rule exists

Advises that kpi has measurement method. This is a review signal about metric definition, measurement frequency, data source, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: KPI ' ' lacks a definition, measurementFrequency, or dataSource. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.metricDefinition.hasText() and self.measurementFrequency.hasText() and self.dataSource.hasText()
```

This rule reads: `metricDefinition`, `measurementFrequency`, `dataSource`, `labelText`.

### Diagnostic and repair

> Warning [CIM-KPI-002] KPI ' ' lacks a definition, measurementFrequency, or dataSource. Suggestion: define how the metric is calculated, how often it is measured, and where the measurement comes from.

**How to fix it:**

define how the metric is calculated, how often it is measured, and where the measurement comes from.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `KPIHasRecognisableOperator`

**Context:** `CIM!KPI`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:115`

### Why this rule exists

Advises that kpi has recognisable operator. This is a review signal about operator, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: KPI ' ' uses a non-standard operator. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.operator.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : Sequence{"<", "<=", ">", ">=", "=", "==", "between", "increases", "decreases"}.includes(self.operator)
```

This rule reads: `operator`, `labelText`.

### Diagnostic and repair

> Warning [CIM-KPI-003] KPI ' ' uses a non-standard operator. Suggestion: use one of <, <=, >, >=, =, ==, between, increases, or decreases, or document the operator in metricDefinition.

**How to fix it:**

use one of <, <=, >, >=, =, ==, between, increases, or decreases, or document the operator in metricDefinition.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StakeholderConcernIsDeclared`

**Context:** `CIM!Stakeholder`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:128`

### Why this rule exists

Advises that stakeholder concern is declared. This is a review signal about stakeholder type, concern, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Stakeholder ' ' has no stakeholderType or concern. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stakeholderType.hasText() and self.concern.hasText()
```

This rule reads: `stakeholderType`, `concern`, `labelText`.

### Diagnostic and repair

> Warning [CIM-ORG-001] Stakeholder ' ' has no stakeholderType or concern. Suggestion: capture the stakeholder category and the concern they represent, such as operations, compliance, customer support, or product ownership.

**How to fix it:**

capture the stakeholder category and the concern they represent, such as operations, compliance, customer support, or product ownership.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ActorHasTypeAndTrustLevel`

**Context:** `CIM!Actor`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:140`

### Why this rule exists

Checks that actor has type and trust level. The actor element owns the evidence for this decision, including actor type, trust level, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Actor ' ' must declare actorType and trustLevel.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.actorType.isDefined() and self.trustLevel.isDefined()
```

This rule reads: `actorType`, `trustLevel`, `labelText`.

### Diagnostic and repair

> Error [CIM-ACTOR-001] Actor ' ' must declare actorType and trustLevel. Suggestion: classify who/what the actor is and how much the business trusts it; this drives authorization, privacy, and boundary validations.

**How to fix it:**

classify who/what the actor is and how much the business trusts it; this drives authorization, privacy, and boundary validations.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `HumanActorHasRole`

**Context:** `CIM!Actor`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:148`

### Why this rule exists

Advises that human actor has role. This is a review signal about actor type, plays roles, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Human Actor ' ' has no assigned Role. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.actorType = CIM!ActorType#HUMAN
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.playsRoles.notEmpty()
```

This rule reads: `actorType`, `playsRoles`, `labelText`.

### Diagnostic and repair

> Warning [CIM-ACTOR-002] Human Actor ' ' has no assigned Role. Suggestion: add at least one role describing the responsibility and business permissions used when issuing commands or queries.

**How to fix it:**

add at least one role describing the responsibility and business permissions used when issuing commands or queries.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ExternalActorDeclaresAuthExpectations`

**Context:** `CIM!Actor`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:157`

### Why this rule exists

Checks that external actor declares auth expectations. The actor element owns the evidence for this decision, including actor type, trust level, authentication expectation, authorization expectation, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: External or untrusted Actor ' ' lacks authenticationExpectation or authorizationExpectation.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.actorType = CIM!ActorType#EXTERNAL_SYSTEM) or (self.actorType = CIM!ActorType#EXTERNAL_ORGANIZATION) or (self.trustLevel = CIM!TrustLevel#UNTRUSTED_EXTERNAL) or (self.trustLevel = CIM!TrustLevel#REGULATED_EXTERNAL)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.authenticationExpectation.hasText() and self.authorizationExpectation.hasText()
```

This rule reads: `actorType`, `trustLevel`, `authenticationExpectation`, `authorizationExpectation`, `labelText`.

### Diagnostic and repair

> Error [CIM-ACTOR-003] External or untrusted Actor ' ' lacks authenticationExpectation or authorizationExpectation. Suggestion: describe how identity is established and which business permissions or policies constrain the actor.

**How to fix it:**

describe how identity is established and which business permissions or policies constrain the actor.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PrivilegedRoleHasPermissionSummary`

**Context:** `CIM!Role`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:174`

### Why this rule exists

Checks that privileged role has permission summary. The role element owns the evidence for this decision, including privileged, responsibility, business permission summary, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Privileged Role ' ' lacks responsibility or businessPermissionSummary.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.privileged = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.responsibility.hasText() and self.businessPermissionSummary.hasText()
```

This rule reads: `privileged`, `responsibility`, `businessPermissionSummary`, `labelText`.

### Diagnostic and repair

> Error [CIM-ROLE-001] Privileged Role ' ' lacks responsibility or businessPermissionSummary. Suggestion: document the privileged responsibility and the business permissions that justify elevated access.

**How to fix it:**

document the privileged responsibility and the business permissions that justify elevated access.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RoleAssignedToActor`

**Context:** `CIM!Role`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:183`

### Why this rule exists

Advises that role assigned to actor. This is a review signal about assigned to, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Role ' ' is not assigned to any Actor. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.assignedTo.notEmpty()
```

This rule reads: `assignedTo`, `labelText`.

### Diagnostic and repair

> Warning [CIM-ROLE-002] Role ' ' is not assigned to any Actor. Suggestion: assign it to the actors who perform that responsibility, or remove the unused role.

**How to fix it:**

assign it to the actors who perform that responsibility, or remove the unused role.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ActorTypeMatchesSubclass`

**Context:** `CIM!ExternalSystem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:195`

### Why this rule exists

Checks that actor type matches subclass. The external system element owns the evidence for this decision, including actor type, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ExternalSystem ' ' must have actorType=EXTERNAL_SYSTEM.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.actorType = CIM!ActorType#EXTERNAL_SYSTEM
```

This rule reads: `actorType`, `labelText`.

### Diagnostic and repair

> Error [CIM-EXT-000] ExternalSystem ' ' must have actorType=EXTERNAL_SYSTEM. Suggestion: align the actorType enum with the concrete ExternalSystem class.

**How to fix it:**

align the actorType enum with the concrete ExternalSystem class.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternalSystemDeclaresPurposeAndTrust`

**Context:** `CIM!ExternalSystem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:203`

### Why this rule exists

Checks that external system declares purpose and trust. The external system element owns the evidence for this decision, including owning organization, business purpose, trust rationale, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ExternalSystem ' ' lacks owningOrganization, businessPurpose, or trustRationale.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.owningOrganization.hasText() and self.businessPurpose.hasText() and self.trustRationale.hasText()
```

This rule reads: `owningOrganization`, `businessPurpose`, `trustRationale`, `labelText`.

### Diagnostic and repair

> Error [CIM-EXT-001] ExternalSystem ' ' lacks owningOrganization, businessPurpose, or trustRationale. Suggestion: identify who owns the system, why the business integrates with it, and why its trust level is acceptable.

**How to fix it:**

identify who owns the system, why the business integrates with it, and why its trust level is acceptable.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternalSystemDataExchangeIsExplicit`

**Context:** `CIM!ExternalSystem`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:212`

### Why this rule exists

Checks that external system data exchange is explicit. The external system element owns the evidence for this decision, including stores business data, sends business events, receives business events, exchanged information, produced events. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ExternalSystem ' ' is marked as exchanging/storing business data but no exchangedInformation or events are linked.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.storesBusinessData = true) or (self.sendsBusinessEvents = true) or (self.receivesBusinessEvents = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.exchangedInformation.notEmpty() or self.producedEvents.notEmpty() or self.consumedEvents.notEmpty()
```

This rule reads: `storesBusinessData`, `sendsBusinessEvents`, `receivesBusinessEvents`, `exchangedInformation`, `producedEvents`, `consumedEvents`, `labelText`.

### Diagnostic and repair

> Error [CIM-EXT-002] ExternalSystem ' ' is marked as exchanging/storing business data but no exchangedInformation or events are linked. Suggestion: connect the information items and business events crossing the boundary.

**How to fix it:**

connect the information items and business events crossing the boundary.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CapabilitySupportsGoal`

**Context:** `CIM!BusinessCapability`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:231`

### Why this rule exists

Checks that capability supports goal. The business capability element owns the evidence for this decision, including supports, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessCapability ' ' supports no BusinessGoal.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.supports.notEmpty()
```

This rule reads: `supports`, `labelText`.

### Diagnostic and repair

> Error [CIM-CAP-001] BusinessCapability ' ' supports no BusinessGoal. Suggestion: link the capability to at least one goal, or remove it if it is outside the CIM scope.

**How to fix it:**

link the capability to at least one goal, or remove it if it is outside the CIM scope.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CapabilityHasOwnerOrResponsibility`

**Context:** `CIM!BusinessCapability`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:239`

### Why this rule exists

Checks that capability has owner or responsibility. The business capability element owns the evidence for this decision, including owner, owner name, responsibility, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessCapability ' ' lacks an owner or responsibility.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.owner.isDefined() or self.ownerName.hasText() or self.responsibility.hasText()
```

This rule reads: `owner`, `ownerName`, `responsibility`, `labelText`.

### Diagnostic and repair

> Error [CIM-CAP-002] BusinessCapability ' ' lacks an owner or responsibility. Suggestion: assign an Actor owner, provide ownerName, or describe the responsibility boundary.

**How to fix it:**

assign an Actor owner, provide ownerName, or describe the responsibility boundary.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CapabilityHasBehaviorOrManagedData`

**Context:** `CIM!BusinessCapability`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:248`

### Why this rule exists

Advises that capability has behavior or managed data. This is a review signal about contains commands, contains queries, contains events, manages entities, owns processes, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: BusinessCapability ' ' has no commands, queries, events, managed entities, or processes. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.containsCommands.notEmpty() or self.containsQueries.notEmpty() or self.containsEvents.notEmpty() or self.managesEntities.notEmpty() or self.ownsProcesses.notEmpty()
```

This rule reads: `containsCommands`, `containsQueries`, `containsEvents`, `managesEntities`, `ownsProcesses`, `labelText`.

### Diagnostic and repair

> Warning [CIM-CAP-003] BusinessCapability ' ' has no commands, queries, events, managed entities, or processes. Suggestion: add the behavior/data it owns, or mark it as out of transformation scope using notes/rationale.

**How to fix it:**

add the behavior/data it owns, or mark it as out of transformation scope using notes/rationale.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CriticalCapabilityHasRequirementsAndNfrs`

**Context:** `CIM!BusinessCapability`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:259`

### Why this rule exists

Advises that critical capability has requirements and nfrs. This is a review signal about criticality, realizes requirements, constrained by, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Core or mission-critical BusinessCapability ' ' lacks realized requirements or non-functional constraints. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.criticality = CIM!CapabilityCriticality#CORE) or (self.criticality = CIM!CapabilityCriticality#MISSION_CRITICAL)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.realizesRequirements.notEmpty() and self.constrainedBy.notEmpty()
```

This rule reads: `criticality`, `realizesRequirements`, `constrainedBy`, `labelText`.

### Diagnostic and repair

> Warning [CIM-CAP-004] Core or mission-critical BusinessCapability ' ' lacks realized requirements or non-functional constraints. Suggestion: connect the capability to functional/business requirements and the relevant NFRs for performance, security, privacy, auditability, or reliability.

**How to fix it:**

connect the capability to functional/business requirements and the relevant NFRs for performance, security, privacy, auditability, or reliability.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CapabilityDependencyIsMeaningful`

**Context:** `CIM!CapabilityDependency`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:274`

### Why this rule exists

Checks that capability dependency is meaningful. The capability dependency element owns the evidence for this decision, including source, target, dependency reason, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: CapabilityDependency ' ' is self-referential or lacks dependencyReason.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.source <> self.target) and self.dependencyReason.hasText()
```

This rule reads: `source`, `target`, `dependencyReason`, `labelText`.

### Diagnostic and repair

> Error [CIM-CAP-005] CapabilityDependency ' ' is self-referential or lacks dependencyReason. Suggestion: connect two distinct capabilities and explain the business reason for the dependency.

**How to fix it:**

connect two distinct capabilities and explain the business reason for the dependency.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CriticalDependencyHasRationale`

**Context:** `CIM!CapabilityDependency`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:283`

### Why this rule exists

Advises that critical dependency has rationale. This is a review signal about critical path, rationale, dependency reason, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Critical-path CapabilityDependency ' ' needs a stronger rationale. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.criticalPath = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rationale.hasText() or self.dependencyReason.hasText()
```

This rule reads: `criticalPath`, `rationale`, `dependencyReason`, `labelText`.

### Diagnostic and repair

> Warning [CIM-CAP-006] Critical-path CapabilityDependency ' ' needs a stronger rationale. Suggestion: document why this dependency is on the critical path and what transformation risk it introduces.

**How to fix it:**

document why this dependency is on the critical path and what transformation risk it introduces.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `BoundedContextHasBoundaryDefinition`

**Context:** `CIM!BoundedContextCandidate`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:296`

### Why this rule exists

Checks that bounded context has boundary definition. The bounded context candidate element owns the evidence for this decision, including language boundary, ownership boundary, label text. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BoundedContextCandidate ' ' lacks languageBoundary or ownershipBoundary.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.languageBoundary.hasText() and self.ownershipBoundary.hasText()
```

This rule reads: `languageBoundary`, `ownershipBoundary`, `labelText`.

### Diagnostic and repair

> Error [CIM-BC-001] BoundedContextCandidate ' ' lacks languageBoundary or ownershipBoundary. Suggestion: state the ubiquitous language boundary and the ownership/team/business boundary.

**How to fix it:**

state the ubiquitous language boundary and the ownership/team/business boundary.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BoundedContextHasScopedContent`

**Context:** `CIM!BoundedContextCandidate`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:304`

### Why this rule exists

Advises that bounded context has scoped content. This is a review signal about capabilities, entities, commands, queries, events, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: BoundedContextCandidate ' ' has no scoped content. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.capabilities.notEmpty() or self.entities.notEmpty() or self.commands.notEmpty() or self.queries.notEmpty() or self.events.notEmpty() or self.policies.notEmpty()
```

This rule reads: `capabilities`, `entities`, `commands`, `queries`, `events`, `policies`, `labelText`.

### Diagnostic and repair

> Warning [CIM-BC-002] BoundedContextCandidate ' ' has no scoped content. Suggestion: add capabilities, entities, commands, queries, events, or policies that belong to this boundary.

**How to fix it:**

add capabilities, entities, commands, queries, events, or policies that belong to this boundary.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CommandsTargetContextOwnedAggregates`

**Context:** `CIM!BoundedContextCandidate`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:315`

### Why this rule exists

Advises that commands target context owned aggregates. This is a review signal about commands, target aggregate, entities, capabilities, label text, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: BoundedContextCandidate ' ' contains commands that target aggregates/entities outside the context without an explicit dependency. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.commands.forAll(commandItem | commandItem.targetAggregate.isUndefined() or (self.entities.includes(commandItem.targetAggregate.root) and commandItem.targetAggregate.members.forAll(member | self.entities.includes(member))) or CIM!CapabilityDependency.all.exists(dep | self.capabilities.includes(dep.source) or self.capabilities.includes(dep.target)) )
```

This rule reads: `commands`, `targetAggregate`, `entities`, `capabilities`, `labelText`.

### Diagnostic and repair

> Warning [CIM-BC-003] BoundedContextCandidate ' ' contains commands that target aggregates/entities outside the context without an explicit dependency. Suggestion: add a CapabilityDependency or integration event for cross-boundary writes.

**How to fix it:**

add a CapabilityDependency or integration event for cross-boundary writes.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `UbiquitousLanguageTermHasDefinition`

**Context:** `CIM!UbiquitousLanguageTerm`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:331`

### Why this rule exists

Checks that ubiquitous language term has definition. The ubiquitous language term element owns the evidence for this decision, including term, definition. At this level, requirements and goals can guide architecture instead of leaving implementers to guess what success means; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: UbiquitousLanguageTerm ' ' has no definition.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.term.hasText() and self.definition.hasText()
```

This rule reads: `term`, `definition`.

### Diagnostic and repair

> Error [CIM-UL-001] UbiquitousLanguageTerm ' ' has no definition. Suggestion: define the term in business language and add synonyms/forbiddenSynonyms where ambiguity is likely.

**How to fix it:**

define the term in business language and add synonyms/forbiddenSynonyms where ambiguity is likely.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `UbiquitousLanguageTermIsUniqueInContext`

**Context:** `CIM!UbiquitousLanguageTerm`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/organization-intent.evl:339`

### Why this rule exists

Advises that ubiquitous language term is unique in context. This is a review signal about term, context, not a cosmetic naming preference. In this part of the model, requirements and goals can guide architecture instead of leaving implementers to guess what success means; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: UbiquitousLanguageTerm ' ' is duplicated in the same bounded context. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.term.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : CIM!UbiquitousLanguageTerm.all.none(termItem | (termItem <> self) and (termItem.term.hasText()) and (termItem.term.toLowerCase() = self.term.toLowerCase()) and (termItem.`context` = self.`context`) )
```

This rule reads: `term`, `context`.

### Diagnostic and repair

> Warning [CIM-UL-002] UbiquitousLanguageTerm ' ' is duplicated in the same bounded context. Suggestion: merge duplicate terms or clarify if the terms intentionally have different meanings.

**How to fix it:**

merge duplicate terms or clarify if the terms intentionally have different meanings.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
