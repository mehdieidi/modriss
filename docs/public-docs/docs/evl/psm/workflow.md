# AWS PSM validation: Workflow

Step Functions and ASL rules protect the executable state graph. They verify definition sources, entry and terminal states, state naming, task targets, JSONPath/JSONata boundaries, waits, choices, retries, and catches so a generated state machine is not merely syntactically shaped but operationally startable.

Source profile: `mde/validation/psm/rules/workflow.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `StateMachineHasRole`

**Context:** `AWSPSM!StepFunctionStateMachine`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:8`

### Why this rule exists

Checks that state machine has role. The step function state machine element owns the evidence for this decision, including role, resource label. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Step Functions state machine has no execution role.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.role.isDefined()
```

This rule reads: `role`, `resourceLabel`.

### Diagnostic and repair

> Step Functions state machine has no execution role. Fix: attach an IamRole trusted by states.amazonaws.com.

**How to fix it:**

attach an IamRole trusted by states.amazonaws.com.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StateMachineHasDefinition`

**Context:** `AWSPSM!StepFunctionStateMachine`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:14`

### Why this rule exists

A Step Functions resource without a definition has no executable workflow. The rule permits the supported definition sources, but insists that one, and only one, owns the state machine body.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.definitionCount() >= 1
```

This rule reads: `definitionCount`, `resourceLabel`.

### Diagnostic and repair

> Step Functions state machine has no definition. Fix: provide definitionUri, definitionString, or aslDocument.

**How to fix it:**

provide definitionUri, definitionString, or aslDocument.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StateMachineShouldUseSingleDefinitionSource`

**Context:** `AWSPSM!StepFunctionStateMachine`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/workflow.evl:20`

### Why this rule exists

Advises that state machine should use single definition source. This is a review signal about definition count, resource label, not a cosmetic naming preference. In this part of the model, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Step Functions state machine defines more than one definition source. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.definitionCount() <= 1
```

This rule reads: `definitionCount`, `resourceLabel`.

### Diagnostic and repair

> Step Functions state machine defines more than one definition source. Fix: keep exactly one of definitionUri, definitionString, or aslDocument to avoid ambiguity.

**How to fix it:**

keep exactly one of definitionUri, definitionString, or aslDocument to avoid ambiguity.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StateMachineAslHasStates`

**Context:** `AWSPSM!StepFunctionStateMachine`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:26`

### Why this rule exists

Checks that state machine asl has states. The step function state machine element owns the evidence for this decision, including asl document, resource label. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Step Functions state machine has an ASL document with no states.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.aslDocument.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.aslDocument.states.notEmpty()
```

This rule reads: `aslDocument`, `resourceLabel`.

### Diagnostic and repair

> Step Functions state machine has an ASL document with no states. Fix: add AslState entries or remove the empty ASL document.

**How to fix it:**

add AslState entries or remove the empty ASL document.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PublishAliasRequiresAliasName`

**Context:** `AWSPSM!StepFunctionStateMachine`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:33`

### Why this rule exists

Checks that publish alias requires alias name. The step function state machine element owns the evidence for this decision, including publish alias, alias name, resource label. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Step Functions state machine publishes an alias but aliasName is empty.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.publishAlias = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.aliasName.hasText()
```

This rule reads: `publishAlias`, `aliasName`, `resourceLabel`.

### Diagnostic and repair

> Step Functions state machine publishes an alias but aliasName is empty. Fix: set aliasName.

**How to fix it:**

set aliasName.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionStateMachineShouldLogAndTrace`

**Context:** `AWSPSM!StepFunctionStateMachine`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/workflow.evl:40`

### Why this rule exists

Advises that production state machine should log and trace. This is a review signal about is production scoped, logging, tracing, resource label, not a cosmetic naming preference. In this part of the model, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped state machine lacks logging or tracing. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.logging.isDefined() and self.tracing.isDefined() and self.tracing.enabled = true
```

This rule reads: `isProductionScoped`, `logging`, `tracing`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped state machine lacks logging or tracing. Fix: add StepFunctionLoggingConfig and enable tracing.

**How to fix it:**

add StepFunctionLoggingConfig and enable tracing.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StartAtReferencesExistingState`

**Context:** `AWSPSM!AslDocument`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:50`

### Why this rule exists

`StartAt` is the first state lookup performed by Step Functions. A name that does not match an actual state fails before business logic begins, so this is an executable-reference check rather than a naming preference.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.startAt.hasText() and self.hasStateNamed(self.startAt)
```

This rule reads: `startAt`, `hasStateNamed`.

### Diagnostic and repair

> ASL document startAt value does not match any stateName. Fix: set startAt to the name of an existing AslState.

**How to fix it:**

set startAt to the name of an existing AslState.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StateNamesUnique`

**Context:** `AWSPSM!AslDocument`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:56`

### Why this rule exists

ASL stores states by name, effectively using the name as a JSON key. Duplicate names erase the distinction between two modeled states and make transitions impossible to resolve deterministically.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.duplicateStateNames().isEmpty()
```

This rule reads: `duplicateStateNames`.

### Diagnostic and repair

> ASL document contains duplicate state names: , . Fix: give every AslState a unique stateName.

**How to fix it:**

give every AslState a unique stateName.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AslHasTerminalState`

**Context:** `AWSPSM!AslDocument`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:62`

### Why this rule exists

Every execution path needs a defined completion or failure endpoint. Requiring a terminal state prevents a state machine from being generated as an open graph that can never finish cleanly.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.states.exists(stateItem | (stateItem.end = true) or stateItem.isKindOf(AWSPSM!AslSucceedState) or stateItem.isKindOf(AWSPSM!AslFailState))
```

This rule reads: `states`, `end`, `isKindOf`.

### Diagnostic and repair

> ASL document has no terminal state. Fix: mark one state end=true or add AslSucceedState/AslFailState.

**How to fix it:**

mark one state end=true or add AslSucceedState/AslFailState.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `NonTerminalStateHasNextOrTerminalType`

**Context:** `AWSPSM!AslState`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:72`

### Why this rule exists

Checks that non terminal state has next or terminal type. The asl state element owns the evidence for this decision, including end, next state, next state name, is kind of, state name. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ASL state is not terminal and has no next state.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.end = true) or self.nextState.isDefined() or self.nextStateName.hasText() or self.isKindOf(AWSPSM!AslSucceedState) or self.isKindOf(AWSPSM!AslFailState) or self.isKindOf(AWSPSM!AslChoiceState)
```

This rule reads: `end`, `nextState`, `nextStateName`, `isKindOf`, `stateName`.

### Diagnostic and repair

> ASL state is not terminal and has no next state. Fix: set nextState/nextStateName, mark end=true, or use a terminal/choice state class.

**How to fix it:**

set nextState/nextStateName, mark end=true, or use a terminal/choice state class.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TerminalStateDoesNotHaveNext`

**Context:** `AWSPSM!AslState`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:79`

### Why this rule exists

Checks that terminal state does not have next. The asl state element owns the evidence for this decision, including end, is kind of, next state, next state name, state name. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: Terminal ASL state also defines a next state.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.end = true) or self.isKindOf(AWSPSM!AslSucceedState) or self.isKindOf(AWSPSM!AslFailState)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.nextState.isUndefined() and (not self.nextStateName.hasText())
```

This rule reads: `end`, `isKindOf`, `nextState`, `nextStateName`, `stateName`.

### Diagnostic and repair

> Terminal ASL state also defines a next state. Fix: remove nextState/nextStateName from terminal states.

**How to fix it:**

remove nextState/nextStateName from terminal states.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TaskStateHasResource`

**Context:** `AWSPSM!AslTaskState`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:90`

### Why this rule exists

Checks that task state has resource. The asl task state element owns the evidence for this decision, including resource, invoked resource, state name. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: AslTaskState has no resource or invokedResource.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resource.hasText() or self.invokedResource.isDefined()
```

This rule reads: `resource`, `invokedResource`, `stateName`.

### Diagnostic and repair

> AslTaskState has no resource or invokedResource. Fix: set the AWS service ARN/resource string or reference the invoked PSM resource.

**How to fix it:**

set the AWS service ARN/resource string or reference the invoked PSM resource.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LambdaIntegrationHasFunctionTarget`

**Context:** `AWSPSM!AslTaskState`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:96`

### Why this rule exists

Prevents the Lambda optimized integration from being emitted without a function target. The asl task state element owns the evidence for this decision, including resource, invoked resource, arguments json, parameters json, state name. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ASL Lambda integration has no function target.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.resource = 'arn:aws:states:::lambda:invoke' or self.resource = 'arn:aws:states:::lambda:invoke.waitForTaskToken'
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.invokedResource.isDefined() or self.argumentsJson.hasText() or self.parametersJson.hasText()
```

This rule reads: `resource`, `invokedResource`, `argumentsJson`, `parametersJson`, `stateName`.

### Diagnostic and repair

> ASL Lambda integration has no function target. Fix: reference invokedResource or provide FunctionName in Arguments/Parameters.

**How to fix it:**

reference invokedResource or provide FunctionName in Arguments/Parameters.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `NestedExecutionHasStateMachineTarget`

**Context:** `AWSPSM!AslTaskState`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:103`

### Why this rule exists

Prevents nested execution integration from omitting the target state machine ARN. The asl task state element owns the evidence for this decision, including resource, invoked resource, arguments json, state name. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ASL nested execution has no target state machine arguments.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.resource = 'arn:aws:states:::states:startExecution.sync'
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.invokedResource.isDefined() and self.argumentsJson.hasText()
```

This rule reads: `resource`, `invokedResource`, `argumentsJson`, `stateName`.

### Diagnostic and repair

> ASL nested execution has no target state machine arguments. Fix: reference the nested Step Functions resource and provide StateMachineArn in Arguments.

**How to fix it:**

reference the nested Step Functions resource and provide StateMachineArn in Arguments.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `JsonataStateDoesNotUseJsonPathFields`

**Context:** `AWSPSM!AslState`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:112`

### Why this rule exists

JSONata and JSONPath use different ASL data-shaping fields. Mixing them is not a harmless extra setting: it makes the state document express two incompatible evaluation models.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.queryLanguage = 'JSONata'
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.inputPath.hasText() and not self.outputPath.hasText() and not self.resultPath.hasText() and not self.parametersJson.hasText()
```

This rule reads: `queryLanguage`, `inputPath`, `outputPath`, `resultPath`, `parametersJson`, `stateName`.

### Diagnostic and repair

> JSONata ASL state uses JSONPath-only fields. Fix: use Arguments/Output or change the state query language to JSONPath.

**How to fix it:**

use Arguments/Output or change the state query language to JSONPath.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `WaitStateHasTiming`

**Context:** `AWSPSM!AslWaitState`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:122`

### Why this rule exists

This rule checks a semantic decision. The asl wait state element owns the evidence for this decision, including timeout seconds, state name. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ASL wait state has no timing value.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.timeoutSeconds.isDefined() and self.timeoutSeconds >= 0
```

This rule reads: `timeoutSeconds`, `stateName`.

### Diagnostic and repair

> ASL wait state has no timing value. Fix: provide a supported duration or model callback/event completion before deployment.

**How to fix it:**

provide a supported duration or model callback/event completion before deployment.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ChoiceStateHasChoices`

**Context:** `AWSPSM!AslChoiceState`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:131`

### Why this rule exists

Checks that choice state has choices. The asl choice state element owns the evidence for this decision, including choices, state name. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: AslChoiceState has no choices.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.choices.notEmpty()
```

This rule reads: `choices`, `stateName`.

### Diagnostic and repair

> AslChoiceState has no choices. Fix: add one or more AslChoiceRule entries.

**How to fix it:**

add one or more AslChoiceRule entries.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RetryRuleRangesValid`

**Context:** `AWSPSM!AslRetryRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:140`

### Why this rule exists

Retry parameters determine how long a failure can occupy capacity and how aggressively it is repeated. The lower bounds ensure the modeled policy describes a real delay, attempt count, and backoff rather than an impossible value.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : ((self.intervalSeconds.isUndefined()) or (self.intervalSeconds >= 1)) and ((self.maxAttempts.isUndefined()) or (self.maxAttempts >= 0)) and ((self.backoffRate.isUndefined()) or (self.backoffRate >= 1.0)) and ((self.maxDelaySeconds.isUndefined()) or (self.maxDelaySeconds >= 1))
```

This rule reads: `intervalSeconds`, `maxAttempts`, `backoffRate`, `maxDelaySeconds`.

### Diagnostic and repair

> ASL retry rule has invalid range values. Fix: use intervalSeconds >= 1, maxAttempts >= 0, backoffRate >= 1.0, and maxDelaySeconds >= 1.

**How to fix it:**

use intervalSeconds >= 1, maxAttempts >= 0, backoffRate >= 1.0, and maxDelaySeconds >= 1.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CatchRuleHasErrorsAndNext`

**Context:** `AWSPSM!AslCatchRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:152`

### Why this rule exists

Checks that catch rule has errors and next. The asl catch rule element owns the evidence for this decision, including error equals, next state. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ASL catch rule lacks errorEquals or nextState.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.errorEquals.notEmpty() and self.nextState.isDefined()
```

This rule reads: `errorEquals`, `nextState`.

### Diagnostic and repair

> ASL catch rule lacks errorEquals or nextState. Fix: list errors to catch and reference the recovery state.

**How to fix it:**

list errors to catch and reference the recovery state.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ChoiceRuleHasConditionAndNext`

**Context:** `AWSPSM!AslChoiceRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/workflow.evl:161`

### Why this rule exists

Checks that choice rule has condition and next. The asl choice rule element owns the evidence for this decision, including condition expression, variable, next state. At this level, the ASL document can start, traverse, recover, and terminate using data-processing fields that match its query language; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: ASL choice rule lacks condition/variable or nextState.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.conditionExpression.hasText() or self.variable.hasText()) and self.nextState.isDefined()
```

This rule reads: `conditionExpression`, `variable`, `nextState`.

### Diagnostic and repair

> ASL choice rule lacks condition/variable or nextState. Fix: define the condition and target state for the choice branch.

**How to fix it:**

define the condition and target state for the choice branch.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
