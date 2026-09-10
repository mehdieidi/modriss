# PIM validation: Compute

Compute rules ensure that every function has a responsibility, contract, reachability path, and compatible state behavior. They also surface the operational consequences of external calls, public exposure, duration, idempotency, and event publication.

Source profile: `mde/validation/pim/rules/compute.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `FunctionResponsibilityRequired`

**Context:** `PIM!Function`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:8`

### Why this rule exists

A function is an execution unit, not a blank container. Its responsibility explains why it exists and gives reviewers a way to detect accidental orchestration or business logic duplication.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.responsibility.hasText() or self.handlerResponsibility.hasText()
```

This rule reads: `responsibility`, `handlerResponsibility`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-001] Function ' ' does not describe its responsibility. Fix: fill responsibility and/or handlerResponsibility with the business operation this function performs.

**How to fix it:**

fill responsibility and/or handlerResponsibility with the business operation this function performs.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `FunctionContractRequired`

**Context:** `PIM!Function`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:14`

### Why this rule exists

Without a contract, a function can be generated but its input and output boundaries remain guesswork. The rule keeps invocation wiring, validation, and tests anchored to an explicit shape.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.contract.isDefined()
```

This rule reads: `contract`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-002] Function ' ' has no FunctionContract. Fix: add a contract with input, output and error schemas as appropriate.

**How to fix it:**

add a contract with input, output and error schemas as appropriate.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `FunctionMustBeReachable`

**Context:** `PIM!Function`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:20`

### Why this rule exists

An unreachable function is dead architecture: it consumes review and deployment attention while no modeled trigger or workflow can invoke it. Requiring a path also exposes missing integration work early.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.hasAnyIncomingBinding()
```

This rule reads: `hasAnyIncomingBinding`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-003] Function ' ' is not reachable from any trigger, API route, workflow task or choice step, event channel, or event type. Fix: connect it through a Trigger, ApiRoute.functionIntegration, TaskStep.invokesFunction, ChoiceStep.invokesFunction, EventChannel producer/consumer, or EventType producedBy/consumedBy reference.

**How to fix it:**

connect it through a Trigger, ApiRoute.functionIntegration, TaskStep.invokesFunction, ChoiceStep.invokesFunction, EventChannel producer/consumer, or EventType producedBy/consumedBy reference.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StateChangingFunctionNeedsIdempotency`

**Context:** `PIM!Function`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:26`

### Why this rule exists

Retries are normal in serverless delivery, so a state-changing function must say how a repeated request is recognized. This rule protects business state from duplicate effects rather than merely checking a retry setting.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.needsIdempotencyPolicy()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.idempotency.isDefined() and self.idempotency.keySource.hasText()
```

This rule reads: `needsIdempotencyPolicy`, `idempotency`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-004] Function ' ' changes state, publishes events, retries, or requires idempotency but lacks a usable IdempotencyPolicy. Fix: attach an IdempotencyPolicy with keySource, scope, expiration and storage decision.

**How to fix it:**

attach an IdempotencyPolicy with keySource, scope, expiration and storage decision.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ReadsStateMustReferenceStores`

**Context:** `PIM!Function`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:33`

### Why this rule exists

A function that reads state without naming its store has no reproducible data dependency. Making the reference explicit lets transformations derive permissions, bindings, and operational documentation.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.readsState.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.reads.notEmpty()
```

This rule reads: `readsState`, `reads`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-005] Function ' ' declares readsState but references no storage in reads. Fix: link the DataStore/ObjectStore it reads or set readsState to false.

**How to fix it:**

link the DataStore/ObjectStore it reads or set readsState to false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `WritesStateMustReferenceStores`

**Context:** `PIM!Function`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:40`

### Why this rule exists

Writes are where data loss and unauthorized mutation become real deployment risks. The rule requires the destination to be modeled so ownership, access, consistency, and recovery policies can be checked.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.writesState.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.writes.notEmpty()
```

This rule reads: `writesState`, `writes`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-006] Function ' ' declares writesState but references no storage in writes. Fix: link the DataStore/ObjectStore it writes or set writesState to false.

**How to fix it:**

link the DataStore/ObjectStore it writes or set writesState to false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PublishesEventsMustReferenceEventTypes`

**Context:** `PIM!Function`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:47`

### Why this rule exists

Event publication is a contract with downstream consumers. Naming the event types prevents a function from emitting an unspecified payload that cannot be versioned or subscribed to safely.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.publishesEvents.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.publishes.notEmpty()
```

This rule reads: `publishesEvents`, `publishes`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-007] Function ' ' declares publishesEvents but has no published EventType. Fix: add entries to publishes or set publishesEvents to false.

**How to fix it:**

add entries to publishes or set publishesEvents to false.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternalCallsNeedResilienceAndTimeout`

**Context:** `PIM!Function`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/compute.evl:54`

### Why this rule exists

An external call introduces latency and failure outside the function's control. The critique makes timeout and resilience behavior explicit before an adapter is generated with an accidental default.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.callsAdapters.notEmpty() or self.requiresNetworkAccess.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resilience.isDefined() and self.timeout.isDefined()
```

This rule reads: `callsAdapters`, `requiresNetworkAccess`, `resilience`, `timeout`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-008] Function ' ' calls external systems or needs network access but lacks resilience/timeout policy. Suggested fix: attach TimeoutPolicy and ResiliencePolicy with retry, timeout and fallback/dead-letter decisions.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `FunctionDurationEstimatesShouldBeConsistent`

**Context:** `PIM!Function`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/compute.evl:61`

### Why this rule exists

Duration estimates affect timeout, cost, concurrency, and workflow design. A mismatch between expected execution and declared limits is a design inconsistency worth resolving before provider binding.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.expectedAverageDurationMs.isDefined() and self.expectedP95DurationMs.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.expectedAverageDurationMs > 0 and self.expectedP95DurationMs >= self.expectedAverageDurationMs
```

This rule reads: `expectedAverageDurationMs`, `expectedP95DurationMs`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-009] Function ' ' has inconsistent duration estimates. Suggested fix: make expectedAverageDurationMs positive and expectedP95DurationMs greater than or equal to the average.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PublicFunctionShouldHaveSecurityPolicy`

**Context:** `PIM!Function`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/compute.evl:68`

### Why this rule exists

A publicly reachable function has no protective API boundary to inherit from. Requiring an explicit security policy makes that exposure a conscious design decision rather than an omission.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.publicEntryPoint.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.securityPolicies.exists(policyItem | policyItem.authenticationRequired.isTrue() or policyItem.authorizationRequired.isTrue())
```

This rule reads: `publicEntryPoint`, `securityPolicies`, `authenticationRequired`, `authorizationRequired`, `displayName`.

### Diagnostic and repair

> [PIM-FUNC-010] Public entry function ' ' has no authentication or authorization policy. Suggested fix: attach a SecurityPolicy/AuthPolicy/AuthorizationPolicy or model the public access through an ApiRoute with auth.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ExactlyOneInvocationTarget`

**Context:** `PIM!Trigger`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:79`

### Why this rule exists

An invocation may point to a function, workflow, or other supported action, but it must resolve to one of them. Multiple targets turn one modeled event into ambiguous runtime behavior.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.`function`.isDefined() and self.startsWorkflow.isUndefined()) or (self.`function`.isUndefined() and self.startsWorkflow.isDefined())
```

This rule reads: `function`, `startsWorkflow`, `displayName`.

### Diagnostic and repair

> [PIM-TRIGGER-000] Trigger ' ' must target exactly one function or workflow.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TriggerTargetsFunctionOrWorkflow`

**Context:** `PIM!Trigger`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/compute.evl:86`

### Why this rule exists

A trigger that points nowhere cannot create executable architecture. This rule checks the connection from event source to work unit before it is flattened into provider-specific event configuration.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.`function`.isDefined() or self.startsWorkflow.isDefined()
```

This rule reads: `function`, `startsWorkflow`, `displayName`.

### Diagnostic and repair

> [PIM-TRIGGER-001] Trigger ' ' does not invoke a function or start a workflow. Fix: contain it under Function.triggers or set startsWorkflow.

**How to fix it:**

contain it under Function.triggers or set startsWorkflow.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DisabledTriggerShouldExplainWhy`

**Context:** `PIM!Trigger`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/compute.evl:92`

### Why this rule exists

A disabled trigger can be intentional during rollout, deprecation, or incident response. Recording the reason prevents a silent omission from being mistaken for a forgotten connection.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.enabled.isDefined() and not self.enabled
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rationale.hasText() or self.reviewNotes.hasText()
```

This rule reads: `enabled`, `rationale`, `reviewNotes`, `displayName`.

### Diagnostic and repair

> [PIM-TRIGGER-002] Trigger ' ' is disabled without rationale. Suggested fix: explain whether it is intentionally disabled for draft, phased rollout, or deprecation.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
