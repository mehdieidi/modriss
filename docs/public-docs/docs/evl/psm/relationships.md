# AWS PSM validation: Relationships

Relationship-view rules check that the generated integration picture agrees with the deployable resources and permissions it claims to summarize. They are consistency checks across resources, not substitutes for the resource-specific rules.

Source profile: `mde/validation/psm/rules/relationships.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `RelationshipViewHasEndpoints`

**Context:** `AWSPSM!AwsRelationshipView`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/relationships.evl:8`

### Why this rule exists

An integration view is useful only when it names both sides of the relationship. This catches generated or manually created view records that look complete but cannot be followed to real resources.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.source.isDefined() and self.target.isDefined()
```

This rule reads: `source`, `target`.

### Diagnostic and repair

> Relationship view has missing source or target. Fix: attach both source and target AwsResource references or remove the incomplete view.

**How to fix it:**

attach both source and target AwsResource references or remove the incomplete view.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ApiLambdaViewMatchesDeployableObjects`

**Context:** `AWSPSM!ApiGatewayLambdaIntegrationView`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/relationships.evl:17`

### Why this rule exists

Checks that api lambda view matches deployable objects. The api gateway lambda integration view element owns the evidence for this decision, including route, integration, function, source, target. At this level, integration views continue to describe real deployable resources and permissions rather than becoming misleading diagrams; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: API Gateway to Lambda relationship view is inconsistent with route/integration/function references.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.route.isDefined() and self.integration.isDefined() and self.`function`.isDefined() and self.route.integration = self.integration and self.integration.lambdaTarget = self.`function` and self.source = self.route and self.target = self.`function`
```

This rule reads: `route`, `integration`, `function`, `source`, `target`.

### Diagnostic and repair

> API Gateway to Lambda relationship view is inconsistent with route/integration/function references. Fix: align source=route, target=function, route.integration=integration, and integration.lambdaTarget=function.

**How to fix it:**

align source=route, target=function, route.integration=integration, and integration.lambdaTarget=function.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ApiLambdaPermissionRecommended`

**Context:** `AWSPSM!ApiGatewayLambdaIntegrationView`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/relationships.evl:24`

### Why this rule exists

Advises that api lambda permission recommended. This is a review signal about permission, not a cosmetic naming preference. In this part of the model, integration views continue to describe real deployable resources and permissions rather than becoming misleading diagrams; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: API Gateway to Lambda relationship view has no LambdaPermission. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.permission.isDefined()
```

This rule reads: `permission`.

### Diagnostic and repair

> API Gateway to Lambda relationship view has no LambdaPermission. Fix: add LambdaPermission allowing API Gateway to invoke the function, unless permission is generated elsewhere.

**How to fix it:**

add LambdaPermission allowing API Gateway to invoke the function, unless permission is generated elsewhere.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `EventBridgeLambdaViewMatchesDeployableObjects`

**Context:** `AWSPSM!EventBridgeLambdaTargetView`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/relationships.evl:33`

### Why this rule exists

Checks that event bridge lambda view matches deployable objects. The event bridge lambda target view element owns the evidence for this decision, including rule, target row, function, source, target. At this level, integration views continue to describe real deployable resources and permissions rather than becoming misleading diagrams; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: EventBridge to Lambda relationship view is inconsistent.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rule.isDefined() and self.targetRow.isDefined() and self.`function`.isDefined() and self.rule.targets.includes(self.targetRow) and self.targetRow.targetResource = self.`function` and self.source = self.rule and self.target = self.`function`
```

This rule reads: `rule`, `targetRow`, `function`, `source`, `target`.

### Diagnostic and repair

> EventBridge to Lambda relationship view is inconsistent. Fix: ensure targetRow belongs to rule, targetRow.targetResource references function, and view source/target match.

**How to fix it:**

ensure targetRow belongs to rule, targetRow.targetResource references function, and view source/target match.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventBridgeLambdaPermissionRecommended`

**Context:** `AWSPSM!EventBridgeLambdaTargetView`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/relationships.evl:40`

### Why this rule exists

Advises that event bridge lambda permission recommended. This is a review signal about permission, not a cosmetic naming preference. In this part of the model, integration views continue to describe real deployable resources and permissions rather than becoming misleading diagrams; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: EventBridge to Lambda relationship view has no LambdaPermission. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.permission.isDefined()
```

This rule reads: `permission`.

### Diagnostic and repair

> EventBridge to Lambda relationship view has no LambdaPermission. Fix: add LambdaPermission allowing EventBridge to invoke the function, unless permission is generated elsewhere.

**How to fix it:**

add LambdaPermission allowing EventBridge to invoke the function, unless permission is generated elsewhere.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SnsLambdaViewMatchesDeployableObjects`

**Context:** `AWSPSM!SnsLambdaSubscriptionView`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/relationships.evl:49`

### Why this rule exists

Checks that sns lambda view matches deployable objects. The sns lambda subscription view element owns the evidence for this decision, including topic, subscription, function, source, target. At this level, integration views continue to describe real deployable resources and permissions rather than becoming misleading diagrams; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SNS to Lambda relationship view is inconsistent.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.topic.isDefined() and self.subscription.isDefined() and self.`function`.isDefined() and self.topic.subscriptions.includes(self.subscription) and self.subscription.endpointResource = self.`function` and self.source = self.topic and self.target = self.`function`
```

This rule reads: `topic`, `subscription`, `function`, `source`, `target`.

### Diagnostic and repair

> SNS to Lambda relationship view is inconsistent. Fix: ensure subscription belongs to topic, endpointResource references function, and view source/target match.

**How to fix it:**

ensure subscription belongs to topic, endpointResource references function, and view source/target match.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SnsLambdaPermissionRecommended`

**Context:** `AWSPSM!SnsLambdaSubscriptionView`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/relationships.evl:56`

### Why this rule exists

Advises that sns lambda permission recommended. This is a review signal about permission, not a cosmetic naming preference. In this part of the model, integration views continue to describe real deployable resources and permissions rather than becoming misleading diagrams; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: SNS to Lambda relationship view has no LambdaPermission. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.permission.isDefined()
```

This rule reads: `permission`.

### Diagnostic and repair

> SNS to Lambda relationship view has no LambdaPermission. Fix: add LambdaPermission allowing SNS to invoke the function, unless permission is generated elsewhere.

**How to fix it:**

add LambdaPermission allowing SNS to invoke the function, unless permission is generated elsewhere.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SqsLambdaViewMatchesMapping`

**Context:** `AWSPSM!SqsLambdaEventSourceView`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/relationships.evl:65`

### Why this rule exists

Checks that sqs lambda view matches mapping. The sqs lambda event source view element owns the evidence for this decision, including mapping, queue, function, source, target. At this level, integration views continue to describe real deployable resources and permissions rather than becoming misleading diagrams; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SQS to Lambda relationship view is inconsistent with its mapping.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.mapping.isDefined() and self.queue.isDefined() and self.`function`.isDefined() and self.mapping.queue = self.queue and self.mapping.`function` = self.`function` and self.source = self.queue and self.target = self.`function`
```

This rule reads: `mapping`, `queue`, `function`, `source`, `target`.

### Diagnostic and repair

> SQS to Lambda relationship view is inconsistent with its mapping. Fix: align mapping.queue, mapping.function, source, and target references.

**How to fix it:**

align mapping.queue, mapping.function, source, and target references.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StepFunctionEventBridgeViewMatchesTarget`

**Context:** `AWSPSM!StepFunctionEventBridgeTargetView`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/relationships.evl:75`

### Why this rule exists

Checks that step function event bridge view matches target. The step function event bridge target view element owns the evidence for this decision, including rule, target row, state machine, source, target. At this level, integration views continue to describe real deployable resources and permissions rather than becoming misleading diagrams; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: EventBridge to Step Functions relationship view is inconsistent.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.rule.isDefined() and self.targetRow.isDefined() and self.stateMachine.isDefined() and self.rule.targets.includes(self.targetRow) and self.targetRow.targetResource = self.stateMachine and self.source = self.rule and self.target = self.stateMachine
```

This rule reads: `rule`, `targetRow`, `stateMachine`, `source`, `target`.

### Diagnostic and repair

> EventBridge to Step Functions relationship view is inconsistent. Fix: ensure targetRow belongs to rule, targetResource references stateMachine, and view source/target match.

**How to fix it:**

ensure targetRow belongs to rule, targetResource references stateMachine, and view source/target match.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
