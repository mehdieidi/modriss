# PIM validation — Workflow

Workflow rules protect the PIM execution graph: it must have entry and termination, reachable steps, valid transitions, unambiguous task actions, recoverable errors, and explicit compensation where the business needs it.

Source profile: `mde/validation/pim/rules/workflow.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `WorkflowHasStartStep`

**Context:** `PIM!Workflow`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:8`

### Why this rule exists

The workflow transformer needs a deterministic entry point; collection order is not a business rule. Requiring a start step makes the first executable action explicit.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.startSteps().notEmpty()
```

This rule reads: `startSteps`, `displayName`.

### Diagnostic and repair

> [PIM-WF-000A] Workflow ' ' must contain at least one StartStep.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `WorkflowHasEndSteps`

**Context:** `PIM!Workflow`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:14`

### Why this rule exists

Checks that end steps belong to workflow. The workflow element owns the evidence for this decision, including end steps, steps, display name. At this level, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-WF-000B] Workflow ' ' must contain at least one SuccessEndStep or FailureEndStep.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.endSteps().notEmpty() and self.endSteps().forAll(stepItem | self.steps.includes(stepItem))
```

This rule reads: `endSteps`, `steps`, `displayName`.

### Diagnostic and repair

> [PIM-WF-000B] Workflow ' ' must contain at least one SuccessEndStep or FailureEndStep.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ReachableFromStart`

**Context:** `PIM!Workflow`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:20`

### Why this rule exists

A step outside the start-reachable graph will never run, no matter how carefully it is configured. The constraint catches orphaned work that diagrams and collections can otherwise hide.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.steps.forAll(stepItem | stepItem.isKindOf(PIM!StartStep) or stepItem.incomingTransitions().notEmpty() or stepItem.rationale.hasText())
```

This rule reads: `steps`, `isKindOf`, `incomingTransitions`, `rationale`, `displayName`.

### Diagnostic and repair

> [PIM-WF-000C] Workflow ' ' contains steps that are not reachable from the start step. Fix transitions or document intentionally detached steps.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `WorkflowStartAndEndStepsAreContained`

**Context:** `PIM!Workflow`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:27`

### Why this rule exists

Checks that workflow start and end steps are contained. The workflow element owns the evidence for this decision, including start steps, steps, end steps, display name. At this level, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-WF-001] Workflow ' ' has start/end steps outside its contained steps.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.startSteps().forAll(stepItem | self.steps.includes(stepItem)) and self.endSteps().forAll(stepItem | self.steps.includes(stepItem))
```

This rule reads: `startSteps`, `steps`, `endSteps`, `displayName`.

### Diagnostic and repair

> [PIM-WF-001] Workflow ' ' has start/end steps outside its contained steps. Fix: model StartStep and end steps as contained WorkflowStep elements.

**How to fix it:**

model StartStep and end steps as contained WorkflowStep elements.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `WorkflowHasAtLeastOneEndStep`

**Context:** `PIM!Workflow`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:34`

### Why this rule exists

Checks that workflow has at least one end step. The workflow element owns the evidence for this decision, including end steps, display name. At this level, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-WF-002] Workflow ' ' has no end steps.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.endSteps().notEmpty()
```

This rule reads: `endSteps`, `displayName`.

### Diagnostic and repair

> [PIM-WF-002] Workflow ' ' has no end steps. Fix: add one or more SuccessEndStep or FailureEndStep elements.

**How to fix it:**

add one or more SuccessEndStep or FailureEndStep elements.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StatefulWorkflowNeedsIdempotency`

**Context:** `PIM!Workflow`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/workflow.evl:40`

### Why this rule exists

A stateful workflow may resume after retries or partial failure. Its business-level idempotency decision determines whether resumption repeats a side effect or safely recognizes work already done.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.stateful.isTrue() or self.compensationRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.idempotency.isDefined()
```

This rule reads: `stateful`, `compensationRequired`, `idempotency`, `displayName`.

### Diagnostic and repair

> [PIM-WF-004] Stateful or compensation-capable workflow ' ' lacks idempotency policy. Suggested fix: attach IdempotencyPolicy to prevent duplicate side effects in retries/restarts.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `NonStartStepShouldHaveIncomingTransition`

**Context:** `PIM!WorkflowStep`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/workflow.evl:51`

### Why this rule exists

Advises that non start step should have incoming transition. This is a review signal about workflow, is kind of, incoming transitions, display name, not a cosmetic naming preference. In this part of the model, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-WF-008] Workflow step ' ' is not a start step and has no incoming transition. Suggested fix: connect it from another step or remove the unreachable step. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.workflow.isDefined() and not self.isKindOf(PIM!StartStep)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.incomingTransitions().exists(transitionItem | transitionItem.workflow = self.workflow)
```

This rule reads: `workflow`, `isKindOf`, `incomingTransitions`, `displayName`.

### Diagnostic and repair

> [PIM-WF-008] Workflow step ' ' is not a start step and has no incoming transition. Suggested fix: connect it from another step or remove the unreachable step.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `TimedStepShouldUseTimeoutPolicy`

**Context:** `PIM!WorkflowStep`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/workflow.evl:58`

### Why this rule exists

Advises that timed step should use timeout policy. This is a review signal about timeout seconds, retry, catch handlers, rationale, display name, not a cosmetic naming preference. In this part of the model, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-WF-009] Workflow step ' ' has timeoutSeconds but no retry/catch/rationale. Suggested fix: define retry/error handling or explain why timeout failure needs no handling. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.timeoutSeconds.isDefined() and self.timeoutSeconds > 0
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.retry.isDefined() or self.catchHandlers.notEmpty() or self.rationale.hasText()
```

This rule reads: `timeoutSeconds`, `retry`, `catchHandlers`, `rationale`, `displayName`.

### Diagnostic and repair

> [PIM-WF-009] Workflow step ' ' has timeoutSeconds but no retry/catch/rationale. Suggested fix: define retry/error handling or explain why timeout failure needs no handling.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ChoiceHasAtLeastTwoTransitions`

**Context:** `PIM!ChoiceStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:69`

### Why this rule exists

A choice with one branch is not expressing a choice; it is adding branching machinery without an alternative. Requiring two paths protects the meaning of conditional business flow.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.outgoingTransitions().atLeastNMatch(transitionItem | true, 2)
```

This rule reads: `outgoingTransitions`, `displayName`.

### Diagnostic and repair

> [PIM-WF-005] Choice step ' ' has fewer than two outgoing transitions. Fix: add at least two transitions with conditions/default handling.

**How to fix it:**

add at least two transitions with conditions/default handling.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EndStepsHaveNoOutgoingTransitions`

**Context:** `PIM!SuccessEndStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:79`

### Why this rule exists

An end step declares completion. An outgoing transition contradicts that declaration and gives transformation code two incompatible interpretations of whether the workflow is finished.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.outgoingTransitions().isEmpty()
```

This rule reads: `outgoingTransitions`, `displayName`.

### Diagnostic and repair

> [PIM-WF-006] End workflow step ' ' has outgoing transitions. Fix: remove outgoing transitions from terminal end steps.

**How to fix it:**

remove outgoing transitions from terminal end steps.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EndStepsHaveNoOutgoingTransitions`

**Context:** `PIM!FailureEndStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:89`

### Why this rule exists

An end step declares completion. An outgoing transition contradicts that declaration and gives transformation code two incompatible interpretations of whether the workflow is finished.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.outgoingTransitions().isEmpty()
```

This rule reads: `outgoingTransitions`, `displayName`.

### Diagnostic and repair

> [PIM-WF-006] End workflow step ' ' has outgoing transitions. Fix: remove outgoing transitions from terminal end steps.

**How to fix it:**

remove outgoing transitions from terminal end steps.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TaskStepInvokesExactlyOneAction`

**Context:** `PIM!TaskStep`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:99`

### Why this rule exists

A task is the point at which the workflow asks something to happen. Exactly one action keeps retries, compensation, permissions, and generated state-machine targets aligned with that request.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.actionCount() = 1
```

This rule reads: `actionCount`, `displayName`.

### Diagnostic and repair

> [PIM-WF-007] Task step ' ' must invoke exactly one function, adapter or nested workflow. Fix: set one of invokesFunction, invokesAdapter or nestedWorkflow and clear the others.

**How to fix it:**

set one of invokesFunction, invokesAdapter or nestedWorkflow and clear the others.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SameWorkflow`

**Context:** `PIM!WorkflowTransition`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:109`

### Why this rule exists

Checks that same workflow. The workflow transition element owns the evidence for this decision, including source, target, workflow, display name. At this level, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-WF-010A] Transition ' ' must connect steps in its owning workflow.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.source.isDefined() and self.target.isDefined() and self.workflow.isDefined() and self.source.workflow = self.workflow and self.target.workflow = self.workflow
```

This rule reads: `source`, `target`, `workflow`, `displayName`.

### Diagnostic and repair

> [PIM-WF-010A] Transition ' ' must connect steps in its owning workflow.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TransitionStaysInsideWorkflow`

**Context:** `PIM!WorkflowTransition`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:116`

### Why this rule exists

Checks that transition stays inside workflow. The workflow transition element owns the evidence for this decision, including source, target, workflow, display name. At this level, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-WF-010] Transition ' ' connects steps outside its owning workflow.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.source.isDefined() and self.target.isDefined() and self.workflow.isDefined() and self.source.workflow = self.workflow and self.target.workflow = self.workflow
```

This rule reads: `source`, `target`, `workflow`, `displayName`.

### Diagnostic and repair

> [PIM-WF-010] Transition ' ' connects steps outside its owning workflow. Fix: set workflow, source and target, and use source/target steps contained by the same workflow.

**How to fix it:**

set workflow, source and target, and use source/target steps contained by the same workflow.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ConditionalTransitionShouldHaveConditionUnlessDefault`

**Context:** `PIM!WorkflowTransition`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/workflow.evl:123`

### Why this rule exists

Advises that conditional transition should have condition unless default. This is a review signal about default transition, condition expression, display name, not a cosmetic naming preference. In this part of the model, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-WF-011] Transition ' ' has no conditionExpression and is not marked defaultTransition. Suggested fix: add a condition or mark it as the default branch. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.defaultTransition.isTrue() or self.conditionExpression.hasText()
```

This rule reads: `defaultTransition`, `conditionExpression`, `displayName`.

### Diagnostic and repair

> [PIM-WF-011] Transition ' ' has no conditionExpression and is not marked defaultTransition. Suggested fix: add a condition or mark it as the default branch.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `NextStepInSameWorkflow`

**Context:** `PIM!ErrorHandler`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:133`

### Why this rule exists

Checks that next step in same workflow. The error handler element owns the evidence for this decision, including next step, step, display name. At this level, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-WF-012A] ErrorHandler ' ' routes to a step outside the owning workflow.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.nextStep.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.step.isDefined() and self.step.workflow.isDefined() and self.nextStep.workflow = self.step.workflow
```

This rule reads: `nextStep`, `step`, `displayName`.

### Diagnostic and repair

> [PIM-WF-012A] ErrorHandler ' ' routes to a step outside the owning workflow. Fix: choose a nextStep in the same workflow.

**How to fix it:**

choose a nextStep in the same workflow.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ErrorHandlerHasRecoveryTarget`

**Context:** `PIM!ErrorHandler`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:140`

### Why this rule exists

Catching an error without a recovery destination only hides the failure. The rule requires the model to say whether execution retries, compensates, terminates, or moves to a human decision.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.nextStep.isDefined() or self.handlerFunction.isDefined()
```

This rule reads: `nextStep`, `handlerFunction`, `displayName`.

### Diagnostic and repair

> [PIM-WF-012] ErrorHandler ' ' has neither nextStep nor handlerFunction. Fix: route recovery to a step or handler function.

**How to fix it:**

route recovery to a step or handler function.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ErrorHandlerShouldSelectErrors`

**Context:** `PIM!ErrorHandler`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/workflow.evl:146`

### Why this rule exists

Advises that error handler should select errors. This is a review signal about error selector, recovery action, display name, not a cosmetic naming preference. In this part of the model, the execution graph has one understandable path through entry, work, failure handling, compensation, and completion; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-WF-013] ErrorHandler ' ' lacks errorSelector or recoveryAction. Suggested fix: state which errors it handles and how recovery proceeds. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.errorSelector.hasText() and self.recoveryAction.hasText()
```

This rule reads: `errorSelector`, `recoveryAction`, `displayName`.

### Diagnostic and repair

> [PIM-WF-013] ErrorHandler ' ' lacks errorSelector or recoveryAction. Suggested fix: state which errors it handles and how recovery proceeds.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CompensationPolicyIsRootOwned`

**Context:** `PIM!CompensationPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:156`

### Why this rule exists

Compensation changes the business outcome of a broader process, so it must belong to the process root rather than an arbitrary nested step. That ownership makes recovery scope reviewable.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : rootPolicyIds().includes(self.`id`)
```

This rule reads: `id`, `displayName`.

### Diagnostic and repair

> [PIM-WF-013A] CompensationPolicy ' ' must be owned by PIMModel.policies and referenced from StartStep.compensation or TaskStep.compensation.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CompensationPolicyHasAction`

**Context:** `PIM!CompensationPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/workflow.evl:162`

### Why this rule exists

A compensation policy that names a condition but no action is an intention without a recovery operation. The rule prevents failed work from being declared handled when nothing can undo or offset it.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.compensationFunctions.notEmpty() or self.compensationEvents.notEmpty() or self.compensationStrategy.hasText()
```

This rule reads: `compensationFunctions`, `compensationEvents`, `compensationStrategy`, `displayName`.

### Diagnostic and repair

> [PIM-WF-014] CompensationPolicy ' ' has no compensation action. Fix: add compensation functions, compensation events or a clear strategy.

**How to fix it:**

add compensation functions, compensation events or a clear strategy.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
