# AWS PSM validation: Observability

CloudWatch rules require alarms and metrics to have the information needed to act on them. Production observability is treated as an operational control: a metric without a threshold or an alarm without an action is not meaningful protection.

Source profile: `mde/validation/psm/rules/observability.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `ProductionLogRetentionExplicit`

**Context:** `AWSPSM!CloudWatchLogGroup`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/observability.evl:8`

### Why this rule exists

The rule checks whether production log retention explicit. The cloud watch log group element provides the relevant evidence through is production scoped, retention days, resource label. At this level, metrics and alarms can support an operator's response instead of merely existing as decorative monitoring objects. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Production-scoped log group has no explicit retentionDays.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.retentionDays.isDefined() and self.retentionDays > 0
```

This rule reads: `isProductionScoped`, `retentionDays`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped log group has no explicit retentionDays. Fix: set a positive retention period aligned to the data retention policy.

**How to fix it:**

set a positive retention period aligned to the data retention policy.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionLogGroupShouldUseKms`

**Context:** `AWSPSM!CloudWatchLogGroup`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/observability.evl:15`

### Why this rule exists

The rule checks whether production log group should use kms. It examines is production scoped, kms key, resource label. Within this part of the model, metrics and alarms can support an operator's response instead of merely existing as decorative monitoring objects. The gap is Production-scoped log group has no KMS key. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.kmsKey.isDefined()
```

This rule reads: `isProductionScoped`, `kmsKey`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped log group has no KMS key. Fix: attach kmsKey if customer-managed encryption is required.

**How to fix it:**

attach kmsKey if customer-managed encryption is required.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `AlarmHasMetricAndThreshold`

**Context:** `AWSPSM!CloudWatchAlarm`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/observability.evl:25`

### Why this rule exists

The rule checks whether alarm has metric and threshold. The cloud watch alarm element provides the relevant evidence through namespace, metric name, threshold, comparison operator, resource label. At this level, metrics and alarms can support an operator's response instead of merely existing as decorative monitoring objects. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: CloudWatch alarm is missing namespace, metricName, threshold, or comparisonOperator.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.namespace.hasText() and self.metricName.hasText() and self.threshold.isDefined() and self.comparisonOperator.isDefined()
```

This rule reads: `namespace`, `metricName`, `threshold`, `comparisonOperator`, `resourceLabel`.

### Diagnostic and repair

> CloudWatch alarm is missing namespace, metricName, threshold, or comparisonOperator. Fix: complete the metric and threshold configuration.

**How to fix it:**

complete the metric and threshold configuration.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `AlarmEvaluationSettingsValid`

**Context:** `AWSPSM!CloudWatchAlarm`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/observability.evl:31`

### Why this rule exists

The rule checks whether alarm evaluation settings valid. The cloud watch alarm element provides the relevant evidence through period, evaluation periods, datapoints to alarm, resource label. At this level, metrics and alarms can support an operator's response instead of merely existing as decorative monitoring objects. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: CloudWatch alarm has invalid evaluation settings.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : ((self.period.isUndefined()) or (self.period > 0)) and ((self.evaluationPeriods.isUndefined()) or (self.evaluationPeriods > 0)) and ((self.datapointsToAlarm.isUndefined()) or (self.datapointsToAlarm > 0)) and (self.datapointsToAlarm.isUndefined() or self.evaluationPeriods.isUndefined() or self.datapointsToAlarm <= self.evaluationPeriods)
```

This rule reads: `period`, `evaluationPeriods`, `datapointsToAlarm`, `resourceLabel`.

### Diagnostic and repair

> CloudWatch alarm has invalid evaluation settings. Fix: use positive period/evaluationPeriods/datapointsToAlarm and ensure datapointsToAlarm <= evaluationPeriods.

**How to fix it:**

use positive period/evaluationPeriods/datapointsToAlarm and ensure datapointsToAlarm <= evaluationPeriods.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionAlarmShouldHaveActions`

**Context:** `AWSPSM!CloudWatchAlarm`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/observability.evl:40`

### Why this rule exists

The rule checks whether production alarm should have actions. It examines monitored resource, alarm action arns, alarm action resources, resource label. Within this part of the model, metrics and alarms can support an operator's response instead of merely existing as decorative monitoring objects. The gap is Alarm monitors a production-scoped resource but has no alarm actions. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.monitoredResource.isDefined() and self.monitoredResource.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.alarmActionArns.notEmpty() or self.alarmActionResources.notEmpty()
```

This rule reads: `monitoredResource`, `alarmActionArns`, `alarmActionResources`, `resourceLabel`.

### Diagnostic and repair

> Alarm monitors a production-scoped resource but has no alarm actions. Fix: add SNS, incident, or automation actions.

**How to fix it:**

add SNS, incident, or automation actions.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `CompositeAlarmShouldHaveActions`

**Context:** `AWSPSM!CloudWatchCompositeAlarm`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/observability.evl:50`

### Why this rule exists

The rule checks whether composite alarm should have actions. It examines alarm action arns, alarm action resources, resource label. Within this part of the model, metrics and alarms can support an operator's response instead of merely existing as decorative monitoring objects. The gap is Composite alarm has no alarm actions. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.alarmActionArns.notEmpty() or self.alarmActionResources.notEmpty()
```

This rule reads: `alarmActionArns`, `alarmActionResources`, `resourceLabel`.

### Diagnostic and repair

> Composite alarm has no alarm actions. Fix: add notification or incident actions, or document why observation-only is acceptable.

**How to fix it:**

add notification or incident actions, or document why observation-only is acceptable.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
