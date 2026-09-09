# PIM validation — Policies

Policy rules turn resilience, timeout, idempotency, rate, batching, ordering, caching, backup, retention, cost, observability, CORS, and data-protection intent into bounded architecture decisions rather than provider defaults.

Source profile: `mde/validation/pim/rules/policies.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `RetryPolicyBounded`

**Context:** `PIM!RetryPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:8`

### Why this rule exists

Checks that retry policy bounded. The retry policy element owns the evidence for this decision, including max attempts, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-001] RetryPolicy ' ' must have maxAttempts between 1 and 10.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.maxAttempts.isDefined() and self.maxAttempts > 0 and self.maxAttempts <= 10
```

This rule reads: `maxAttempts`, `displayName`.

### Diagnostic and repair

> [PIM-POL-001] RetryPolicy ' ' must have maxAttempts between 1 and 10. Fix: set a bounded retry count to avoid infinite or excessive retries.

**How to fix it:**

set a bounded retry count to avoid infinite or excessive retries.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RetryBackoffIsValid`

**Context:** `PIM!RetryPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:14`

### Why this rule exists

Checks that retry backoff is valid. The retry policy element owns the evidence for this decision, including initial delay seconds, max delay seconds, backoff rate, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-002] RetryPolicy ' ' has invalid delay/backoff values.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.initialDelaySeconds.isUndefined() or self.initialDelaySeconds >= 0) and (self.maxDelaySeconds.isUndefined() or self.maxDelaySeconds >= 0) and (self.backoffRate.isUndefined() or self.backoffRate >= 1.0)
```

This rule reads: `initialDelaySeconds`, `maxDelaySeconds`, `backoffRate`, `displayName`.

### Diagnostic and repair

> [PIM-POL-002] RetryPolicy ' ' has invalid delay/backoff values. Fix: use non-negative delays and a backoffRate of at least 1.0.

**How to fix it:**

use non-negative delays and a backoffRate of at least 1.0.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RequiredDeadLetterPolicyHasChannel`

**Context:** `PIM!DeadLetterPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:26`

### Why this rule exists

Checks that required dead letter policy has channel. The dead letter policy element owns the evidence for this decision, including required, dead letter channel, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-003] DeadLetterPolicy ' ' is required but has no deadLetterChannel.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.required.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.deadLetterChannel.isDefined()
```

This rule reads: `required`, `deadLetterChannel`, `displayName`.

### Diagnostic and repair

> [PIM-POL-003] DeadLetterPolicy ' ' is required but has no deadLetterChannel. Fix: link a queue/topic/channel/object carrier for failed messages.

**How to fix it:**

link a queue/topic/channel/object carrier for failed messages.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TimeoutPolicyIsRootOwned`

**Context:** `PIM!TimeoutPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:37`

### Why this rule exists

Checks that timeout policy is root owned. The timeout policy element owns the evidence for this decision, including id, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-003A] TimeoutPolicy ' ' must be owned by PIMModel.policies and referenced from functions/routes/resilience policies.

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

> [PIM-POL-003A] TimeoutPolicy ' ' must be owned by PIMModel.policies and referenced from functions/routes/resilience policies.

**How to fix it:**

Make the model satisfy the check shown above. Start with the referenced features, then re-run the appropriate EVL profile; do not silence the diagnostic by changing an unrelated display field.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `TimeoutPolicyIsPositive`

**Context:** `PIM!TimeoutPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:43`

### Why this rule exists

Checks that timeout policy is positive. The timeout policy element owns the evidence for this decision, including timeout seconds, client timeout seconds, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-004] TimeoutPolicy ' ' has invalid timeout values.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.timeoutSeconds.isDefined() and self.timeoutSeconds > 0 and (self.clientTimeoutSeconds.isUndefined() or self.clientTimeoutSeconds > 0) and (self.clientTimeoutSeconds.isUndefined() or self.clientTimeoutSeconds <= self.timeoutSeconds)
```

This rule reads: `timeoutSeconds`, `clientTimeoutSeconds`, `displayName`.

### Diagnostic and repair

> [PIM-POL-004] TimeoutPolicy ' ' has invalid timeout values. Fix: set timeoutSeconds > 0 and make clientTimeoutSeconds positive and not greater than timeoutSeconds.

**How to fix it:**

set timeoutSeconds > 0 and make clientTimeoutSeconds positive and not greater than timeoutSeconds.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `IdempotencyKeyRequired`

**Context:** `PIM!IdempotencyPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:55`

### Why this rule exists

Checks that idempotency key required. The idempotency policy element owns the evidence for this decision, including key source, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-005] IdempotencyPolicy ' ' has no keySource.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.keySource.hasText()
```

This rule reads: `keySource`, `displayName`.

### Diagnostic and repair

> [PIM-POL-005] IdempotencyPolicy ' ' has no keySource. Fix: specify where the idempotency key comes from, such as request header, event id, business id or generated operation id.

**How to fix it:**

specify where the idempotency key comes from, such as request header, event id, business id or generated operation id.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `IdempotencyStoreDecisionRecommended`

**Context:** `PIM!IdempotencyPolicy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:61`

### Why this rule exists

Advises that idempotency store decision recommended. This is a review signal about store required, scope, expiration seconds, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-006] IdempotencyPolicy ' ' lacks store/scope/expiration decision. Suggested fix: state whether storage is required, define the idempotency scope and set expiration for stored keys. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.storeRequired.isDefined() and self.scope.hasText() and (self.expirationSeconds.isUndefined() or self.expirationSeconds > 0)
```

This rule reads: `storeRequired`, `scope`, `expirationSeconds`, `displayName`.

### Diagnostic and repair

> [PIM-POL-006] IdempotencyPolicy ' ' lacks store/scope/expiration decision. Suggested fix: state whether storage is required, define the idempotency scope and set expiration for stored keys.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ConcurrencyPolicyShouldExplainScaling`

**Context:** `PIM!ConcurrencyPolicy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:71`

### Why this rule exists

Advises that concurrency policy should explain scaling. This is a review signal about max concurrency, reserved concurrency hint, scaling rationale, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-007] ConcurrencyPolicy ' ' lacks concurrency limits or scaling rationale. Suggested fix: set max/reserved concurrency and explain why the limit protects downstream services or cost. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.maxConcurrency.isDefined() or self.reservedConcurrencyHint.isDefined()) and self.scalingRationale.hasText()
```

This rule reads: `maxConcurrency`, `reservedConcurrencyHint`, `scalingRationale`, `displayName`.

### Diagnostic and repair

> [PIM-POL-007] ConcurrencyPolicy ' ' lacks concurrency limits or scaling rationale. Suggested fix: set max/reserved concurrency and explain why the limit protects downstream services or cost.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RateLimitPolicyIsPositive`

**Context:** `PIM!RateLimitPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:81`

### Why this rule exists

Checks that rate limit policy is positive. The rate limit policy element owns the evidence for this decision, including requests per second, burst limit, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-008] RateLimitPolicy ' ' has invalid limits.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.requestsPerSecond.isDefined() and self.requestsPerSecond > 0 and (self.burstLimit.isUndefined() or self.burstLimit >= self.requestsPerSecond)
```

This rule reads: `requestsPerSecond`, `burstLimit`, `displayName`.

### Diagnostic and repair

> [PIM-POL-008] RateLimitPolicy ' ' has invalid limits. Fix: set requestsPerSecond > 0 and make burstLimit at least requestsPerSecond if provided.

**How to fix it:**

set requestsPerSecond > 0 and make burstLimit at least requestsPerSecond if provided.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BatchPolicyHasSizeAndFailureDecision`

**Context:** `PIM!BatchPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:92`

### Why this rule exists

Checks that batch policy has size and failure decision. The batch policy element owns the evidence for this decision, including batch size, partial failure handling, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-009] BatchPolicy ' ' needs a positive batchSize and a partialFailureHandling decision.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.batchSize.isDefined() and self.batchSize > 0 and not self.partialFailureHandling.enumIs("UNDECIDED")
```

This rule reads: `batchSize`, `partialFailureHandling`, `displayName`.

### Diagnostic and repair

> [PIM-POL-009] BatchPolicy ' ' needs a positive batchSize and a partialFailureHandling decision. Fix: set batchSize and choose REQUIRED or NOT_REQUIRED with rationale.

**How to fix it:**

set batchSize and choose REQUIRED or NOT_REQUIRED with rationale.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BatchPolicyNeedsRationaleForNoPartialFailureHandling`

**Context:** `PIM!BatchPolicy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:99`

### Why this rule exists

Advises that batch policy needs rationale for no partial failure handling. This is a review signal about partial failure handling, decision rationale, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-010] BatchPolicy ' ' says partial failure handling is not required but gives no rationale. Suggested fix: explain why whole-batch retry/failure is acceptable. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.partialFailureHandling.enumIs("NOT_REQUIRED")
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.decisionRationale.hasText()
```

This rule reads: `partialFailureHandling`, `decisionRationale`, `displayName`.

### Diagnostic and repair

> [PIM-POL-010] BatchPolicy ' ' says partial failure handling is not required but gives no rationale. Suggested fix: explain why whole-batch retry/failure is acceptable.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProductionObservabilityNeedsCorrelation`

**Context:** `PIM!ObservabilityConfig`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:110`

### Why this rule exists

Checks that production observability needs correlation. The observability config element owns the evidence for this decision, including production required, correlation id required, correlation id field, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-011] Production ObservabilityConfig ' ' must require correlation IDs.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.productionRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.correlationIdRequired.isTrue() and self.correlationIdField.hasText()
```

This rule reads: `productionRequired`, `correlationIdRequired`, `correlationIdField`, `displayName`.

### Diagnostic and repair

> [PIM-POL-011] Production ObservabilityConfig ' ' must require correlation IDs. Fix: set correlationIdRequired to true and define correlationIdField.

**How to fix it:**

set correlationIdRequired to true and define correlationIdField.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ObservabilityShouldEnableSignals`

**Context:** `PIM!ObservabilityConfig`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:117`

### Why this rule exists

Advises that observability should enable signals. This is a review signal about logging enabled, metrics enabled, tracing enabled, alarms enabled, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-012] ObservabilityConfig ' ' enables no observability signal. Suggested fix: enable logging, metrics, tracing or alarms according to production needs. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.loggingEnabled.isTrue() or self.metricsEnabled.isTrue() or self.tracingEnabled.isTrue() or self.alarmsEnabled.isTrue()
```

This rule reads: `loggingEnabled`, `metricsEnabled`, `tracingEnabled`, `alarmsEnabled`, `displayName`.

### Diagnostic and repair

> [PIM-POL-012] ObservabilityConfig ' ' enables no observability signal. Suggested fix: enable logging, metrics, tracing or alarms according to production needs.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `StructuredLoggingNeedsFormat`

**Context:** `PIM!LoggingPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:127`

### Why this rule exists

Checks that structured logging needs format. The logging policy element owns the evidence for this decision, including structured logging, log format, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-013] LoggingPolicy ' ' requires structured logging but has no logFormat.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.structuredLogging.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.logFormat.hasText()
```

This rule reads: `structuredLogging`, `logFormat`, `displayName`.

### Diagnostic and repair

> [PIM-POL-013] LoggingPolicy ' ' requires structured logging but has no logFormat. Fix: specify a portable format such as JSON.

**How to fix it:**

specify a portable format such as JSON.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `LoggingShouldIncludeCorrelationId`

**Context:** `PIM!LoggingPolicy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:134`

### Why this rule exists

Advises that logging should include correlation id. This is a review signal about include correlation id, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-014] LoggingPolicy ' ' does not include correlation IDs. Suggested fix: set includeCorrelationId to true so logs can be traced across API, function, event and workflow boundaries. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.includeCorrelationId.isTrue()
```

This rule reads: `includeCorrelationId`, `displayName`.

### Diagnostic and repair

> [PIM-POL-014] LoggingPolicy ' ' does not include correlation IDs. Suggested fix: set includeCorrelationId to true so logs can be traced across API, function, event and workflow boundaries.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `MetricPolicyShouldNameMetricAndUnit`

**Context:** `PIM!MetricPolicy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:144`

### Why this rule exists

Advises that metric policy should name metric and unit. This is a review signal about metric name, unit, statistic, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-015] MetricPolicy ' ' lacks metricName, unit or statistic. Suggested fix: define the metric clearly enough to generate dashboards/alarms. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.metricName.hasText() and self.unit.hasText() and self.statistic.hasText()
```

This rule reads: `metricName`, `unit`, `statistic`, `displayName`.

### Diagnostic and repair

> [PIM-POL-015] MetricPolicy ' ' lacks metricName, unit or statistic. Suggested fix: define the metric clearly enough to generate dashboards/alarms.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `AlertPolicyIsActionable`

**Context:** `PIM!AlertPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:154`

### Why this rule exists

Checks that alert policy is actionable. The alert policy element owns the evidence for this decision, including metric name, condition, threshold, notification target, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-016] AlertPolicy ' ' is incomplete.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.metricName.hasText() and self.condition.hasText() and self.threshold.hasText() and self.notificationTarget.hasText()
```

This rule reads: `metricName`, `condition`, `threshold`, `notificationTarget`, `displayName`.

### Diagnostic and repair

> [PIM-POL-016] AlertPolicy ' ' is incomplete. Fix: specify metricName, condition, threshold and notificationTarget.

**How to fix it:**

specify metricName, condition, threshold and notificationTarget.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `SloShouldBeMeasurable`

**Context:** `PIM!Slo`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:164`

### Why this rule exists

Advises that slo should be measurable. This is a review signal about objective name, metric, target, measurement window, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-017] SLO ' ' is not measurable. Suggested fix: fill objectiveName, metric, target and measurementWindow. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.objectiveName.hasText() and self.metric.hasText() and self.target.hasText() and self.measurementWindow.hasText()
```

This rule reads: `objectiveName`, `metric`, `target`, `measurementWindow`, `displayName`.

### Diagnostic and repair

> [PIM-POL-017] SLO ' ' is not measurable. Suggested fix: fill objectiveName, metric, target and measurementWindow.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CredentialedCorsCannotUseWildcardOrigins`

**Context:** `PIM!CorsPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:174`

### Why this rule exists

Checks that credentialed cors cannot use wildcard origins. The cors policy element owns the evidence for this decision, including credentials allowed, allowed origins, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-018] CorsPolicy ' ' allows credentials with wildcard origins.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.credentialsAllowed.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.allowedOrigins.includes("*")
```

This rule reads: `credentialsAllowed`, `allowedOrigins`, `displayName`.

### Diagnostic and repair

> [PIM-POL-018] CorsPolicy ' ' allows credentials with wildcard origins. Fix: replace '\*' with explicit trusted origins.

**How to fix it:**

replace '\*' with explicit trusted origins.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CorsPolicyShouldDeclareMethodsAndHeaders`

**Context:** `PIM!CorsPolicy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:181`

### Why this rule exists

Advises that cors policy should declare methods and headers. This is a review signal about allowed origins, allowed methods, allowed headers, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-019] CorsPolicy ' ' lacks allowed origins, methods or headers. Suggested fix: list the explicit CORS contract needed by browser clients. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.allowedOrigins.notEmpty() and self.allowedMethods.notEmpty() and self.allowedHeaders.notEmpty()
```

This rule reads: `allowedOrigins`, `allowedMethods`, `allowedHeaders`, `displayName`.

### Diagnostic and repair

> [PIM-POL-019] CorsPolicy ' ' lacks allowed origins, methods or headers. Suggested fix: list the explicit CORS contract needed by browser clients.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProtectedDataHasRetentionDecision`

**Context:** `PIM!DataProtectionPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:191`

### Why this rule exists

Checks that protected data has retention decision. The data protection policy element owns the evidence for this decision, including classification, retention period, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-020] DataProtectionPolicy ' ' classifies data but has no retentionPeriod.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.classification.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.retentionPeriod.hasText()
```

This rule reads: `classification`, `retentionPeriod`, `displayName`.

### Diagnostic and repair

> [PIM-POL-020] DataProtectionPolicy ' ' classifies data but has no retentionPeriod. Fix: define retention to support deletion, compliance and lifecycle generation.

**How to fix it:**

define retention to support deletion, compliance and lifecycle generation.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ClassifiedDataNeedsProtectionDecision`

**Context:** `PIM!DataProtectionPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:198`

### Why this rule exists

Checks that classified data needs protection decision. The data protection policy element owns the evidence for this decision, including classification, encryption required, masking required, tokenization required, access audit required. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-021] DataProtectionPolicy ' ' classifies data but has no protection decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.classification.hasText()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.encryptionRequired.isTrue() or self.maskingRequired.isTrue() or self.tokenizationRequired.isTrue() or self.accessAuditRequired.isTrue()
```

This rule reads: `classification`, `encryptionRequired`, `maskingRequired`, `tokenizationRequired`, `accessAuditRequired`, `displayName`.

### Diagnostic and repair

> [PIM-POL-021] DataProtectionPolicy ' ' classifies data but has no protection decision. Fix: enable encryption, masking, tokenization or access auditing.

**How to fix it:**

enable encryption, masking, tokenization or access auditing.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RetentionPolicyHasPeriod`

**Context:** `PIM!RetentionPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:209`

### Why this rule exists

Checks that retention policy has period. The retention policy element owns the evidence for this decision, including retention period, display name. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-022] RetentionPolicy ' ' has no retentionPeriod.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.retentionPeriod.hasText()
```

This rule reads: `retentionPeriod`, `displayName`.

### Diagnostic and repair

> [PIM-POL-022] RetentionPolicy ' ' has no retentionPeriod. Fix: define the retention duration or lifecycle rule in provider-independent terms.

**How to fix it:**

define the retention duration or lifecycle rule in provider-independent terms.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RequiredBackupPolicyHasObjectives`

**Context:** `PIM!BackupPolicy`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/policies.evl:219`

### Why this rule exists

Advises that required backup policy has objectives. This is a review signal about backup required, backup frequency, recovery point objective, recovery time objective, display name, not a cosmetic naming preference. In this part of the model, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-POL-023] BackupPolicy ' ' requires backup but lacks frequency/RPO/RTO. Suggested fix: define backupFrequency, recoveryPointObjective and recoveryTimeObjective. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.backupRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.backupFrequency.hasText() and self.recoveryPointObjective.hasText() and self.recoveryTimeObjective.hasText()
```

This rule reads: `backupRequired`, `backupFrequency`, `recoveryPointObjective`, `recoveryTimeObjective`, `displayName`.

### Diagnostic and repair

> [PIM-POL-023] BackupPolicy ' ' requires backup but lacks frequency/RPO/RTO. Suggested fix: define backupFrequency, recoveryPointObjective and recoveryTimeObjective.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DataQualityPolicyHasMeasurementRule`

**Context:** `PIM!DataQualityPolicy`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/policies.evl:229`

### Why this rule exists

Checks that data quality policy has measurement rule. The data quality policy element owns the evidence for this decision, including validation required, completeness check required, freshness check required, duplicate detection required, quality dimensions. At this level, resilience, timeout, retention, cost, observability, and data-protection expectations are deliberate rather than provider defaults; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-POL-021A] DataQualityPolicy ' ' lacks quality checks, dimensions or measurementRule.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.validationRequired.isTrue() or self.completenessCheckRequired.isTrue() or self.freshnessCheckRequired.isTrue() or self.duplicateDetectionRequired.isTrue()) and self.qualityDimensions.hasText() and self.measurementRule.hasText()
```

This rule reads: `validationRequired`, `completenessCheckRequired`, `freshnessCheckRequired`, `duplicateDetectionRequired`, `qualityDimensions`, `measurementRule`, `displayName`.

### Diagnostic and repair

> [PIM-POL-021A] DataQualityPolicy ' ' lacks quality checks, dimensions or measurementRule. Fix: model validation/completeness/freshness/duplicate requirements explicitly.

**How to fix it:**

model validation/completeness/freshness/duplicate requirements explicitly.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
