# CIM validation — Process Policy

Process and policy rules check that a business journey has a real beginning, completion, responsibility, branching logic, timing, and recovery story. They are deliberately stricter about meaning than about implementation: a process can be technology-neutral, but it cannot be ownerless or impossible to complete.

Source profile: `mde/validation/cim/rules/process-policy.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `ProcessHasStartAndEnd`

**Context:** `CIM!BusinessProcess`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:8`

### Why this rule exists

A process without an entry and completion cannot be transformed into a reliable workflow. The rule is about business completeness, not diagram polish.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.steps.one(stepItem | stepItem.stepKind = CIM!StepKind#START) and self.steps.exists(stepItem | stepItem.stepKind = CIM!StepKind#END)
```

This rule reads: `steps`, `stepKind`, `labelText`.

### Diagnostic and repair

> Error [CIM-PROC-001] BusinessProcess ' ' must have exactly one START step and at least one END step. Suggestion: add one StartStep and one or more EndSteps, and ensure their stepKind values match.

**How to fix it:**

add one StartStep and one or more EndSteps, and ensure their stepKind values match.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProcessHasTriggerAndCompletionCriterion`

**Context:** `CIM!BusinessProcess`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:18`

### Why this rule exists

Checks that process has trigger and completion criterion. The business process element owns the evidence for this decision, including trigger, triggering event, triggering command, triggering actor, business trigger description. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessProcess ' ' lacks a trigger or completionCriterion.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.trigger.isDefined() or self.triggeringEvent.isDefined() or self.triggeringCommand.isDefined() or self.triggeringActor.isDefined() or self.businessTriggerDescription.hasText()) and self.completionCriterion.hasText()
```

This rule reads: `trigger`, `triggeringEvent`, `triggeringCommand`, `triggeringActor`, `businessTriggerDescription`, `completionCriterion`, `labelText`.

### Diagnostic and repair

> Error [CIM-PROC-002] BusinessProcess ' ' lacks a trigger or completionCriterion. Suggestion: specify what starts the process and the business condition that means the process is complete.

**How to fix it:**

specify what starts the process and the business condition that means the process is complete.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProcessTriggerIsTypedForProduction`

**Context:** `CIM!BusinessProcess`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:28`

### Why this rule exists

Checks that process trigger is typed for production. The business process element owns the evidence for this decision, including trigger, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessProcess ' ' uses a generic trigger that is not a BusinessEvent, Command, or Actor.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.trigger.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.trigger.isKindOf(CIM!BusinessEvent) or self.trigger.isKindOf(CIM!Command) or self.trigger.isKindOf(CIM!Actor)
```

This rule reads: `trigger`, `labelText`.

### Diagnostic and repair

> Error [CIM-PROC-002A] BusinessProcess ' ' uses a generic trigger that is not a BusinessEvent, Command, or Actor. Suggestion: use triggeringEvent, triggeringCommand, triggeringActor, or describe the business trigger explicitly.

**How to fix it:**

use triggeringEvent, triggeringCommand, triggeringActor, or describe the business trigger explicitly.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LongRunningProcessHasTemporalConstraint`

**Context:** `CIM!BusinessProcess`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:37`

### Why this rule exists

Long-running and saga-like processes need an explicit time boundary. Without one, timeout, escalation, retention, and compensation design have no business clock to work from.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.longRunning = true) or (self.processKind = CIM!ProcessKind#LONG_RUNNING) or (self.processKind = CIM!ProcessKind#SAGA_LIKE_BUSINESS_PROCESS)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.temporalConstraints.notEmpty()
```

This rule reads: `longRunning`, `processKind`, `temporalConstraints`, `labelText`.

### Diagnostic and repair

> Error [CIM-PROC-003] Long-running/Saga-like BusinessProcess ' ' has no TemporalConstraint. Suggestion: add deadlines, durations, or ordering constraints that define acceptable process timing.

**How to fix it:**

add deadlines, durations, or ordering constraints that define acceptable process timing.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProcessTransitionsStayInsideProcess`

**Context:** `CIM!BusinessProcess`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:46`

### Why this rule exists

Checks that process transitions stay inside process. The business process element owns the evidence for this decision, including transitions, steps, source, target, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: BusinessProcess ' ' contains a transition whose source or target is not one of its steps.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.transitions.forAll(transitionItem | self.steps.includes(transitionItem.source) and self.steps.includes(transitionItem.target) )
```

This rule reads: `transitions`, `steps`, `source`, `target`, `labelText`.

### Diagnostic and repair

> Error [CIM-PROC-004] BusinessProcess ' ' contains a transition whose source or target is not one of its steps. Suggestion: reconnect the transition to steps contained by the same process.

**How to fix it:**

reconnect the transition to steps contained by the same process.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProcessStepOrderIndexesAreUnique`

**Context:** `CIM!BusinessProcess`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:57`

### Why this rule exists

Checks that process step order indexes are unique. The business process element owns the evidence for this decision, including steps, order index. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: .

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check { var indexedSteps = self.steps.select(stepItem | stepItem.orderIndex.isDefined()); return indexedSteps.collect(stepItem | stepItem.orderIndex).asSet().size() = indexedSteps.size();
```

This rule reads: `steps`, `orderIndex`.

### Diagnostic and repair

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CompensationProcessHasExceptionScenarios`

**Context:** `CIM!BusinessProcess`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:68`

### Why this rule exists

Advises that compensation process has exception scenarios. This is a review signal about compensation expected, exceptions, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: BusinessProcess ' ' expects compensation but has no ExceptionScenario. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.compensationExpected = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.exceptions.notEmpty()
```

This rule reads: `compensationExpected`, `exceptions`, `labelText`.

### Diagnostic and repair

> Warning [CIM-PROC-006] BusinessProcess ' ' expects compensation but has no ExceptionScenario. Suggestion: add exception scenarios and recovery/compensation actions.

**How to fix it:**

add exception scenarios and recovery/compensation actions.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProcessStepHasResponsibility`

**Context:** `CIM!ProcessStep`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:81`

### Why this rule exists

Advises that process step has responsibility. This is a review signal about step kind, responsibility, responsible roles, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: ProcessStep ' ' has no responsibility or responsibleRoles. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.stepKind <> CIM!StepKind#START) and (self.stepKind <> CIM!StepKind#END)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.responsibility.hasText() or self.responsibleRoles.notEmpty()
```

This rule reads: `stepKind`, `responsibility`, `responsibleRoles`, `labelText`.

### Diagnostic and repair

> Warning [CIM-STEP-001] ProcessStep ' ' has no responsibility or responsibleRoles. Suggestion: identify who or what is responsible for this step.

**How to fix it:**

identify who or what is responsible for this step.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StartStepKindMatchesClass`

**Context:** `CIM!StartStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:93`

### Why this rule exists

Checks that start step kind matches class. The start step element owns the evidence for this decision, including step kind, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: StartStep ' ' must have stepKind=START.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stepKind = CIM!StepKind#START
```

This rule reads: `stepKind`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-002] StartStep ' ' must have stepKind=START. Suggestion: set stepKind to START.

**How to fix it:**

set stepKind to START.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EndStepKindMatchesClass`

**Context:** `CIM!EndStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:104`

### Why this rule exists

Checks that end step kind matches class. The end step element owns the evidence for this decision, including step kind, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: EndStep ' ' must have stepKind=END.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stepKind = CIM!StepKind#END
```

This rule reads: `stepKind`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-003] EndStep ' ' must have stepKind=END. Suggestion: set stepKind to END.

**How to fix it:**

set stepKind to END.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CommandStepKindMatchesClass`

**Context:** `CIM!CommandStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:115`

### Why this rule exists

Checks that command step kind matches class. The command step element owns the evidence for this decision, including step kind, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: CommandStep ' ' must have stepKind=COMMAND.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stepKind = CIM!StepKind#COMMAND
```

This rule reads: `stepKind`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-004] CommandStep ' ' must have stepKind=COMMAND. Suggestion: set stepKind to COMMAND and link the command reference.

**How to fix it:**

set stepKind to COMMAND and link the command reference.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `QueryStepKindMatchesClass`

**Context:** `CIM!QueryStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:126`

### Why this rule exists

Checks that query step kind matches class. The query step element owns the evidence for this decision, including step kind, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: QueryStep ' ' must have stepKind=QUERY.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stepKind = CIM!StepKind#QUERY
```

This rule reads: `stepKind`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-005] QueryStep ' ' must have stepKind=QUERY. Suggestion: set stepKind to QUERY and link the query reference.

**How to fix it:**

set stepKind to QUERY and link the query reference.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventStepKindMatchesClass`

**Context:** `CIM!EventStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:137`

### Why this rule exists

Checks that event step kind matches class. The event step element owns the evidence for this decision, including step kind, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: EventStep ' ' must have stepKind=EVENT.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stepKind = CIM!StepKind#EVENT
```

This rule reads: `stepKind`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-006] EventStep ' ' must have stepKind=EVENT. Suggestion: set stepKind to EVENT and link the event reference.

**How to fix it:**

set stepKind to EVENT and link the event reference.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PolicyStepKindMatchesClass`

**Context:** `CIM!PolicyStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:148`

### Why this rule exists

Checks that policy step kind matches class. The policy step element owns the evidence for this decision, including step kind, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: PolicyStep ' ' must have stepKind=POLICY.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stepKind = CIM!StepKind#POLICY
```

This rule reads: `stepKind`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-007] PolicyStep ' ' must have stepKind=POLICY. Suggestion: set stepKind to POLICY and link the policy reference.

**How to fix it:**

set stepKind to POLICY and link the policy reference.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `HumanTaskStepKindAndDescription`

**Context:** `CIM!HumanTaskStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:159`

### Why this rule exists

Checks that human task step kind and description. The human task step element owns the evidence for this decision, including step kind, task description, completion evidence, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: HumanTaskStep ' ' must have stepKind=HUMAN_TASK, taskDescription, and completionEvidence.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.stepKind = CIM!StepKind#HUMAN_TASK) and self.taskDescription.hasText() and self.completionEvidence.hasText()
```

This rule reads: `stepKind`, `taskDescription`, `completionEvidence`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-008] HumanTaskStep ' ' must have stepKind=HUMAN_TASK, taskDescription, and completionEvidence. Suggestion: describe the human task and the evidence that proves completion.

**How to fix it:**

describe the human task and the evidence that proves completion.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternalInteractionStepIsExplicit`

**Context:** `CIM!ExternalInteractionStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:172`

### Why this rule exists

Checks that external interaction step is explicit. The external interaction step element owns the evidence for this decision, including step kind, external system, interaction purpose, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ExternalInteractionStep ' ' lacks stepKind=EXTERNAL_INTERACTION, externalSystem, or interactionPurpose.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.stepKind = CIM!StepKind#EXTERNAL_INTERACTION) and self.externalSystem.isDefined() and self.interactionPurpose.hasText()
```

This rule reads: `stepKind`, `externalSystem`, `interactionPurpose`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-009] ExternalInteractionStep ' ' lacks stepKind=EXTERNAL_INTERACTION, externalSystem, or interactionPurpose. Suggestion: set the correct stepKind, select the external system, and explain the business purpose of the interaction.

**How to fix it:**

set the correct stepKind, select the external system, and explain the business purpose of the interaction.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternalInteractionDeclaresInformation`

**Context:** `CIM!ExternalInteractionStep`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:182`

### Why this rule exists

Advises that external interaction declares information. This is a review signal about exchanged information, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: ExternalInteractionStep ' ' has no exchangedInformation. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.exchangedInformation.notEmpty()
```

This rule reads: `exchangedInformation`, `labelText`.

### Diagnostic and repair

> Warning [CIM-STEP-010] ExternalInteractionStep ' ' has no exchangedInformation. Suggestion: link the information items crossing the external boundary.

**How to fix it:**

link the information items crossing the external boundary.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DecisionStepHasDecisionLogic`

**Context:** `CIM!DecisionStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:193`

### Why this rule exists

Checks that decision step has decision logic. The decision step element owns the evidence for this decision, including step kind, condition, decision table, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DecisionStep ' ' lacks stepKind=DECISION or decision logic.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.stepKind = CIM!StepKind#DECISION) and (self.condition.isDefined() or self.decisionTable.isDefined())
```

This rule reads: `stepKind`, `condition`, `decisionTable`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-011] DecisionStep ' ' lacks stepKind=DECISION or decision logic. Suggestion: set stepKind=DECISION and link either a Condition or a DecisionTable.

**How to fix it:**

set stepKind=DECISION and link either a Condition or a DecisionTable.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DecisionHasBranches`

**Context:** `CIM!DecisionStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:203`

### Why this rule exists

A decision with only one outgoing path is not a decision in the business sense. Requiring alternatives protects the distinction between branching logic and an ordinary step.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.outgoingTransitionCount() >= 2
```

This rule reads: `outgoingTransitionCount`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-012] DecisionStep ' ' has fewer than two outgoing branches. Suggestion: add at least two ProcessTransition elements representing alternative outcomes.

**How to fix it:**

add at least two ProcessTransition elements representing alternative outcomes.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `WaitStepHasDurationOrReason`

**Context:** `CIM!WaitStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:214`

### Why this rule exists

Checks that wait step has duration or reason. The wait step element owns the evidence for this decision, including step kind, duration expression, wait reason, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: WaitStep ' ' lacks stepKind=WAIT or a duration/reason.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.stepKind = CIM!StepKind#WAIT) and (self.durationExpression.hasText() or self.waitReason.hasText())
```

This rule reads: `stepKind`, `durationExpression`, `waitReason`, `labelText`.

### Diagnostic and repair

> Error [CIM-STEP-013] WaitStep ' ' lacks stepKind=WAIT or a duration/reason. Suggestion: set stepKind=WAIT and provide durationExpression or waitReason.

**How to fix it:**

set stepKind=WAIT and provide durationExpression or waitReason.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TransitionConnectsDifferentSteps`

**Context:** `CIM!ProcessTransition`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:228`

### Why this rule exists

Checks that transition connects different steps. The process transition element owns the evidence for this decision, including source, target, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ProcessTransition ' ' connects a step to itself.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.source.isDefined() and self.target.isDefined() and self.source <> self.target
```

This rule reads: `source`, `target`, `labelText`.

### Diagnostic and repair

> Error [CIM-TRANS-001] ProcessTransition ' ' connects a step to itself. Suggestion: connect the transition to a distinct target step.

**How to fix it:**

connect the transition to a distinct target step.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TransitionDoesNotLeaveEndOrEnterStart`

**Context:** `CIM!ProcessTransition`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:236`

### Why this rule exists

Checks that transition does not leave end or enter start. The process transition element owns the evidence for this decision, including source, target, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ProcessTransition ' ' leaves an END step or enters a START step.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.source.isDefined() and self.target.isDefined() and (self.source.stepKind <> CIM!StepKind#END) and (self.target.stepKind <> CIM!StepKind#START)
```

This rule reads: `source`, `target`, `labelText`.

### Diagnostic and repair

> Error [CIM-TRANS-002] ProcessTransition ' ' leaves an END step or enters a START step. Suggestion: remove the invalid transition or reconnect it so flow starts at START and terminates at END.

**How to fix it:**

remove the invalid transition or reconnect it so flow starts at START and terminates at END.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ConditionalTransitionIsLabelled`

**Context:** `CIM!ProcessTransition`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:246`

### Why this rule exists

Advises that conditional transition is labelled. This is a review signal about source, label, condition expression, condition, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Decision transition ' ' has no label, conditionExpression, or Condition. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.source.isDefined() and self.source.stepKind = CIM!StepKind#DECISION
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.label.hasText() or self.conditionExpression.hasText() or self.condition.isDefined()
```

This rule reads: `source`, `label`, `conditionExpression`, `condition`, `labelText`.

### Diagnostic and repair

> Warning [CIM-TRANS-003] Decision transition ' ' has no label, conditionExpression, or Condition. Suggestion: name the branch or attach a condition so users understand why this path is taken.

**How to fix it:**

name the branch or attach a condition so users understand why this path is taken.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DecisionTableRulesHaveUniquePriority`

**Context:** `CIM!DecisionTable`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:259`

### Why this rule exists

Checks that decision table rules have unique priority. The decision table element owns the evidence for this decision, including rules, priority order. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: .

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check { var orderedRules = self.rules.select(ruleItem | ruleItem.priorityOrder.isDefined()); return orderedRules.collect(ruleItem | ruleItem.priorityOrder).asSet().size() = orderedRules.size();
```

This rule reads: `rules`, `priorityOrder`.

### Diagnostic and repair

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `IncompleteDecisionTableHasDefault`

**Context:** `CIM!DecisionTable`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:270`

### Why this rule exists

Advises that incomplete decision table has default. This is a review signal about complete, default outcome, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: DecisionTable ' ' is incomplete but has no defaultOutcome. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : not (self.complete = true)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.defaultOutcome.hasText()
```

This rule reads: `complete`, `defaultOutcome`, `labelText`.

### Diagnostic and repair

> Warning [CIM-DT-003] DecisionTable ' ' is incomplete but has no defaultOutcome. Suggestion: provide a default outcome for unmatched cases.

**How to fix it:**

provide a default outcome for unmatched cases.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DecisionRuleHasConditionAndOutcome`

**Context:** `CIM!DecisionRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:283`

### Why this rule exists

Checks that decision rule has condition and outcome. The decision rule element owns the evidence for this decision, including condition, condition expression, outcome, outcome expression, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: DecisionRule ' ' lacks condition or outcome.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.condition.hasText() or self.conditionExpression.hasExpressionBody()) and (self.outcome.hasText() or self.outcomeExpression.hasExpressionBody())
```

This rule reads: `condition`, `conditionExpression`, `outcome`, `outcomeExpression`, `labelText`.

### Diagnostic and repair

> Error [CIM-DT-004] DecisionRule ' ' lacks condition or outcome. Suggestion: specify when the rule applies and what business outcome it produces, using text fields or typed kernel.Expression objects.

**How to fix it:**

specify when the rule applies and what business outcome it produces, using text fields or typed kernel.Expression objects.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DecisionRuleHasExecutableEffect`

**Context:** `CIM!DecisionRule`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:293`

### Why this rule exists

Advises that decision rule has executable effect. This is a review signal about resulting commands, resulting events, outcome, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: DecisionRule ' ' has no resultingCommands, resultingEvents, or meaningful outcome. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resultingCommands.notEmpty() or self.resultingEvents.notEmpty() or self.outcome.hasText()
```

This rule reads: `resultingCommands`, `resultingEvents`, `outcome`, `labelText`.

### Diagnostic and repair

> Warning [CIM-DT-005] DecisionRule ' ' has no resultingCommands, resultingEvents, or meaningful outcome. Suggestion: connect the rule to the commands/events it causes or make the outcome explicit.

**How to fix it:**

connect the rule to the commands/events it causes or make the outcome explicit.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PolicyEitherReactsOrGuards`

**Context:** `CIM!Policy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:305`

### Why this rule exists

Checks that policy either reacts or guards. The policy element owns the evidence for this decision, including triggered by, guards, constrains queries, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Policy ' ' neither reacts to events nor guards commands/queries.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.triggeredBy.notEmpty() or self.guards.notEmpty() or self.constrainsQueries.notEmpty()
```

This rule reads: `triggeredBy`, `guards`, `constrainsQueries`, `labelText`.

### Diagnostic and repair

> Error [CIM-POL-001] Policy ' ' neither reacts to events nor guards commands/queries. Suggestion: link triggeredBy events, guarded commands, constrained queries, or split this policy into a more specific policy.

**How to fix it:**

link triggeredBy events, guarded commands, constrained queries, or split this policy into a more specific policy.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PolicyHasRuleDefinition`

**Context:** `CIM!Policy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:316`

### Why this rule exists

Checks that policy has rule definition. The policy element owns the evidence for this decision, including natural language rule, expression model, expression language, expression, decision table. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Policy ' ' has no naturalLanguageRule, executable expression, or decisionTable.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.naturalLanguageRule.hasText() or self.expressionModel.hasExpressionBody() or (self.expressionLanguage.isDefined() and self.expression.hasText()) or self.decisionTable.isDefined()
```

This rule reads: `naturalLanguageRule`, `expressionModel`, `expressionLanguage`, `expression`, `decisionTable`, `labelText`.

### Diagnostic and repair

> Error [CIM-POL-002] Policy ' ' has no naturalLanguageRule, executable expression, or decisionTable. Suggestion: define the rule in natural language and optionally provide expressionModel.body or a DecisionTable.

**How to fix it:**

define the rule in natural language and optionally provide expressionModel.body or a DecisionTable.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `MandatoryOrBlockingPolicyHasSeverity`

**Context:** `CIM!Policy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:328`

### Why this rule exists

Checks that mandatory or blocking policy has severity. The policy element owns the evidence for this decision, including enforcement strength, violation severity, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Mandatory/blocking Policy ' ' lacks violationSeverity.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.enforcementStrength = KERNEL!ConstraintStrength#MANDATORY) or (self.enforcementStrength = KERNEL!ConstraintStrength#BLOCKING)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.violationSeverity.isDefined()
```

This rule reads: `enforcementStrength`, `violationSeverity`, `labelText`.

### Diagnostic and repair

> Error [CIM-POL-003] Mandatory/blocking Policy ' ' lacks violationSeverity. Suggestion: set violationSeverity to ERROR, CRITICAL, or BLOCKER according to business impact.

**How to fix it:**

set violationSeverity to ERROR, CRITICAL, or BLOCKER according to business impact.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ReactionPolicyEmitsOutcome`

**Context:** `CIM!Policy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:339`

### Why this rule exists

Advises that reaction policy emits outcome. This is a review signal about policy type, emits commands, emits events, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Reaction Policy ' ' has no emitted commands or events. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.policyType = CIM!PolicyType#REACTION
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.emitsCommands.notEmpty() or self.emitsEvents.notEmpty()
```

This rule reads: `policyType`, `emitsCommands`, `emitsEvents`, `labelText`.

### Diagnostic and repair

> Warning [CIM-POL-004] Reaction Policy ' ' has no emitted commands or events. Suggestion: link the command/event that represents the reaction outcome.

**How to fix it:**

link the command/event that represents the reaction outcome.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ExceptionScenarioIsActionable`

**Context:** `CIM!ExceptionScenario`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:352`

### Why this rule exists

Checks that exception scenario is actionable. The exception scenario element owns the evidence for this decision, including scenario, business impact, recoverable, recovery action, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ExceptionScenario ' ' lacks scenario/businessImpact or a recoveryAction for a recoverable scenario.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.scenario.hasText() and self.businessImpact.hasText() and ((self.recoverable <> true) or self.recoveryAction.hasText())
```

This rule reads: `scenario`, `businessImpact`, `recoverable`, `recoveryAction`, `labelText`.

### Diagnostic and repair

> Error [CIM-EXC-001] ExceptionScenario ' ' lacks scenario/businessImpact or a recoveryAction for a recoverable scenario. Suggestion: describe the exception, its business impact, and how recovery should happen.

**How to fix it:**

describe the exception, its business impact, and how recovery should happen.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CompensationScenarioEmitsEvent`

**Context:** `CIM!ExceptionScenario`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:362`

### Why this rule exists

Advises that compensation scenario emits event. This is a review signal about compensation required, recovery action, resulting events, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: ExceptionScenario ' ' requires compensation but has no recoveryAction or resultingEvents. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.compensationRequired = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.recoveryAction.hasText() and self.resultingEvents.notEmpty()
```

This rule reads: `compensationRequired`, `recoveryAction`, `resultingEvents`, `labelText`.

### Diagnostic and repair

> Warning [CIM-EXC-002] ExceptionScenario ' ' requires compensation but has no recoveryAction or resultingEvents. Suggestion: model the compensation action and event(s) that record completion/failure.

**How to fix it:**

model the compensation action and event(s) that record completion/failure.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `TemporalConstraintHasExpression`

**Context:** `CIM!TemporalConstraint`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/cim/rules/process-policy.evl:375`

### Why this rule exists

Checks that temporal constraint has expression. The temporal constraint element owns the evidence for this decision, including deadline expression, duration expression, ordering expression, label text. At this level, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: TemporalConstraint ' ' has no deadline, duration, or ordering expression.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.deadlineExpression.hasText() or self.durationExpression.hasText() or self.orderingExpression.hasText()
```

This rule reads: `deadlineExpression`, `durationExpression`, `orderingExpression`, `labelText`.

### Diagnostic and repair

> Error [CIM-TIME-001] TemporalConstraint ' ' has no deadline, duration, or ordering expression. Suggestion: provide at least one temporal expression and link constrainedElements.

**How to fix it:**

provide at least one temporal expression and link constrainedElements.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TemporalConstraintHasScope`

**Context:** `CIM!TemporalConstraint`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/cim/rules/process-policy.evl:384`

### Why this rule exists

Advises that temporal constraint has scope. This is a review signal about constrained elements, label text, not a cosmetic naming preference. In this part of the model, business journeys can be refined into workflows with real entry, completion, branching, timing, and recovery; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: TemporalConstraint ' ' has no constrainedElements. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.constrainedElements.notEmpty()
```

This rule reads: `constrainedElements`, `labelText`.

### Diagnostic and repair

> Warning [CIM-TIME-002] TemporalConstraint ' ' has no constrainedElements. Suggestion: link the process, step, event, command, or policy to which the timing rule applies.

**How to fix it:**

link the process, step, event, command, or policy to which the timing rule applies.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
