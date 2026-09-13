# AWS PSM validation: Events

EventBridge rules validate the provider-specific event graph: patterns and schedules are not mixed accidentally, targets are complete and unique, roles and connections match their integration type, and retry/dead-letter behavior is not omitted from critical delivery paths.

Source profile: `mde/validation/psm/rules/events.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `RuleHasPatternOrSchedule`

**Context:** `AWSPSM!EventBridgeRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:8`

### Why this rule exists

The rule checks whether rule has pattern or schedule. The event bridge rule element provides the relevant evidence through event pattern json, schedule expression, event pattern, resource label. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge rule has neither an event pattern nor a schedule expression.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.eventPatternJson.hasText() or self.scheduleExpression.hasText() or self.eventPattern.isDefined()
```

This rule reads: `eventPatternJson`, `scheduleExpression`, `eventPattern`, `resourceLabel`.

### Diagnostic and repair

> EventBridge rule has neither an event pattern nor a schedule expression. Fix: define eventPattern/eventPatternJson for event-driven rules or scheduleExpression for scheduled rules.

**How to fix it:**

define eventPattern/eventPatternJson for event-driven rules or scheduleExpression for scheduled rules.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RuleDoesNotMixPatternAndSchedule`

**Context:** `AWSPSM!EventBridgeRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:14`

### Why this rule exists

The rule checks whether rule does not mix pattern and schedule. The event bridge rule element provides the relevant evidence through event pattern json, event pattern, schedule expression, resource label. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge rule defines both event pattern and schedule expression.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not ((self.eventPatternJson.hasText() or self.eventPattern.isDefined()) and self.scheduleExpression.hasText())
```

This rule reads: `eventPatternJson`, `eventPattern`, `scheduleExpression`, `resourceLabel`.

### Diagnostic and repair

> EventBridge rule defines both event pattern and schedule expression. Fix: split into two rules or keep only the intended trigger type.

**How to fix it:**

split into two rules or keep only the intended trigger type.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RuleHasTargets`

**Context:** `AWSPSM!EventBridgeRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:20`

### Why this rule exists

The rule checks whether rule has targets. The event bridge rule element provides the relevant evidence through targets, resource label. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge rule has no targets.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.targets.notEmpty()
```

This rule reads: `targets`, `resourceLabel`.

### Diagnostic and repair

> EventBridge rule has no targets. Fix: add at least one EventBridgeTarget.

**How to fix it:**

add at least one EventBridgeTarget.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TargetIdsUniqueWithinRule`

**Context:** `AWSPSM!EventBridgeRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:26`

### Why this rule exists

The rule checks whether target ids unique within rule. The event bridge rule element provides the relevant evidence through targets, target id, resource label. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge rule contains duplicate targetId values.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.targets.forAll(targetItem | self.targets.one(otherTarget | otherTarget.targetId = targetItem.targetId))
```

This rule reads: `targets`, `targetId`, `resourceLabel`.

### Diagnostic and repair

> EventBridge rule contains duplicate targetId values. Fix: make targetId unique within the rule.

**How to fix it:**

make targetId unique within the rule.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TargetHasResourceOrArn`

**Context:** `AWSPSM!EventBridgeTarget`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:36`

### Why this rule exists

The rule checks whether target has resource or arn. The event bridge target element provides the relevant evidence through target resource, arn expression, target id. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge target has neither targetResource nor arnExpression.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.targetResource.isDefined() or self.arnExpression.hasText()
```

This rule reads: `targetResource`, `arnExpression`, `targetId`.

### Diagnostic and repair

> EventBridge target has neither targetResource nor arnExpression. Fix: reference a PSM resource or provide an ARN expression.

**How to fix it:**

reference a PSM resource or provide an ARN expression.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CriticalEventTargetsHaveRetryOrDlq`

**Context:** `AWSPSM!EventBridgeTarget`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/events.evl:42`

### Why this rule exists

The rule checks whether critical event targets have retry or dlq. It examines target resource, retry policy, dead letter queue, target id. Within this part of the model, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. The gap is Production-scoped EventBridge target has no explicit retry policy or DLQ. Suggested fix: add AwsRetryPolicy and/or deadLetterQueue when default EventBridge retries are not sufficient. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.targetResource.isDefined() and self.targetResource.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.retryPolicy.isDefined() or self.deadLetterQueue.isDefined()
```

This rule reads: `targetResource`, `retryPolicy`, `deadLetterQueue`, `targetId`.

### Diagnostic and repair

> Production-scoped EventBridge target has no explicit retry policy or DLQ. Suggested fix: add AwsRetryPolicy and/or deadLetterQueue when default EventBridge retries are not sufficient.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SqsFifoTargetHasMessageGroupId`

**Context:** `AWSPSM!EventBridgeTarget`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:49`

### Why this rule exists

The rule checks whether sqs fifo target has message group id. The event bridge target element provides the relevant evidence through target resource, parameters, target id. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge target sends to a FIFO SQS queue without messageGroupId.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.targetResource.isDefined() and self.targetResource.isKindOf(AWSPSM!SqsQueue) and self.targetResource.queueType = AWSPSMENUMS!SqsQueueType#FIFO
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.parameters.isDefined() and self.parameters.isKindOf(AWSPSM!EventBridgeSqsTargetParameters) and self.parameters.messageGroupId.hasText()
```

This rule reads: `targetResource`, `parameters`, `targetId`.

### Diagnostic and repair

> EventBridge target sends to a FIFO SQS queue without messageGroupId. Fix: add EventBridgeSqsTargetParameters.messageGroupId.

**How to fix it:**

add EventBridgeSqsTargetParameters.messageGroupId.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `NonLambdaTargetHasInvokeRole`

**Context:** `AWSPSM!EventBridgeTarget`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:56`

### Why this rule exists

The rule checks whether non lambda target has invoke role. The event bridge target element provides the relevant evidence through target kind, role, role arn, target id. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge target has no invoke role.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.targetKind <> AWSPSMENUMS!EventBridgeTargetKind#LAMBDA and self.targetKind <> AWSPSMENUMS!EventBridgeTargetKind#OTHER_AWS_RESOURCE
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.role.isDefined() or self.roleArn.hasText()
```

This rule reads: `targetKind`, `role`, `roleArn`, `targetId`.

### Diagnostic and repair

> EventBridge target has no invoke role. Fix: attach a target-specific IAM role or roleArn for Step Functions, SQS, SNS, EventBridge bus, API destination, or log group targets.

**How to fix it:**

attach a target-specific IAM role or roleArn for Step Functions, SQS, SNS, EventBridge bus, API destination, or log group targets.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RetryPolicyRangesValid`

**Context:** `AWSPSM!AwsRetryPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:66`

### Why this rule exists

The rule checks whether retry policy ranges valid. The aws retry policy element provides the relevant evidence through maximum retry attempts, maximum event age in seconds. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge retry policy has invalid range values.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : ((self.maximumRetryAttempts.isUndefined()) or ((self.maximumRetryAttempts >= 0) and (self.maximumRetryAttempts <= 185))) and ((self.maximumEventAgeInSeconds.isUndefined()) or ((self.maximumEventAgeInSeconds >= 60) and (self.maximumEventAgeInSeconds <= 86400)))
```

This rule reads: `maximumRetryAttempts`, `maximumEventAgeInSeconds`.

### Diagnostic and repair

> EventBridge retry policy has invalid range values. Fix: set maximumRetryAttempts to 0..185 and maximumEventAgeInSeconds to 60..86400, or remove the fields to use defaults.

**How to fix it:**

set maximumRetryAttempts to 0..185 and maximumEventAgeInSeconds to 60..86400, or remove the fields to use defaults.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ScheduleHasExpressionAndRole`

**Context:** `AWSPSM!EventBridgeSchedule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:76`

### Why this rule exists

The rule checks whether schedule has expression and role. The event bridge schedule element provides the relevant evidence through schedule expression, role, resource label. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge schedule lacks scheduleExpression or role.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.scheduleExpression.hasText() and self.role.isDefined()
```

This rule reads: `scheduleExpression`, `role`, `resourceLabel`.

### Diagnostic and repair

> EventBridge schedule lacks scheduleExpression or role. Fix: set scheduleExpression and attach the IAM role used by the scheduler.

**How to fix it:**

set scheduleExpression and attach the IAM role used by the scheduler.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ScheduleHasTarget`

**Context:** `AWSPSM!EventBridgeSchedule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:82`

### Why this rule exists

The rule checks whether schedule has target. The event bridge schedule element provides the relevant evidence through target, resource label. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge schedule has no resolvable target.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.target.isDefined() and (self.target.targetResource.isDefined() or self.target.arnExpression.hasText())
```

This rule reads: `target`, `resourceLabel`.

### Diagnostic and repair

> EventBridge schedule has no resolvable target. Fix: set EventBridgeSchedule.target with a targetResource or ARN expression.

**How to fix it:**

set EventBridgeSchedule.target with a targetResource or ARN expression.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PipeHasSourceTargetAndRole`

**Context:** `AWSPSM!EventBridgePipe`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:91`

### Why this rule exists

The rule checks whether pipe has source target and role. The event bridge pipe element provides the relevant evidence through source arn, source resource, target arn, target resource, role. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge pipe must have source, target, and role.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.sourceArn.hasText() or self.sourceResource.isDefined()) and (self.targetArn.hasText() or self.targetResource.isDefined()) and self.role.isDefined()
```

This rule reads: `sourceArn`, `sourceResource`, `targetArn`, `targetResource`, `role`, `resourceLabel`.

### Diagnostic and repair

> EventBridge pipe must have source, target, and role. Fix: set sourceArn/sourceResource, targetArn/targetResource, and attach the IAM role.

**How to fix it:**

set sourceArn/sourceResource, targetArn/targetResource, and attach the IAM role.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ApiDestinationHasEndpointAndConnection`

**Context:** `AWSPSM!EventBridgeApiDestination`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:102`

### Why this rule exists

The rule checks whether api destination has endpoint and connection. The event bridge api destination element provides the relevant evidence through invocation endpoint, connection, resource label. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge API destination lacks invocationEndpoint or connection.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.invocationEndpoint.hasText() and self.connection.isDefined()
```

This rule reads: `invocationEndpoint`, `connection`, `resourceLabel`.

### Diagnostic and repair

> EventBridge API destination lacks invocationEndpoint or connection. Fix: set an HTTPS endpoint and attach EventBridgeConnection.

**How to fix it:**

set an HTTPS endpoint and attach EventBridgeConnection.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ConnectionAuthParametersMatchAuthorizationType`

**Context:** `AWSPSM!EventBridgeConnection`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/events.evl:111`

### Why this rule exists

The rule checks whether connection auth parameters match authorization type. The event bridge connection element provides the relevant evidence through authorization type, auth parameters, resource label. At this level, EventBridge rules and targets describe a complete delivery path with the right role, retry, schedule, and dead-letter behavior. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: EventBridge connection uses authParameters that do not match authorizationType.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.authorizationType = AWSPSMENUMS!EventBridgeConnectionAuthorizationType#API_KEY and self.authParameters.isKindOf(AWSPSM!EventBridgeApiKeyAuthParameters)) or (self.authorizationType = AWSPSMENUMS!EventBridgeConnectionAuthorizationType#BASIC and self.authParameters.isKindOf(AWSPSM!EventBridgeBasicAuthParameters)) or (self.authorizationType = AWSPSMENUMS!EventBridgeConnectionAuthorizationType#OAUTH_CLIENT_CREDENTIALS and self.authParameters.isKindOf(AWSPSM!EventBridgeOAuthParameters))
```

This rule reads: `authorizationType`, `authParameters`, `resourceLabel`.

### Diagnostic and repair

> EventBridge connection uses authParameters that do not match authorizationType. Fix: use the concrete auth parameter class for the selected authorization type.

**How to fix it:**

use the concrete auth parameter class for the selected authorization type.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
