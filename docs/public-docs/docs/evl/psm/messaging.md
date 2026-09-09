# AWS PSM validation — Messaging

SQS and SNS rules enforce AWS naming, FIFO semantics, timing ranges, redrive behavior, encryption expectations, subscription targets, and filter completeness. They protect the operational contract that sits below the provider-independent channel model.

Source profile: `mde/validation/psm/rules/messaging.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `FifoQueueNameSuffix`

**Context:** `AWSPSM!SqsQueue`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/messaging.evl:8`

### Why this rule exists

Checks that fifo queue name suffix. The sqs queue element owns the evidence for this decision, including queue type, queue name, resource label. At this level, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: FIFO SQS queue must have a queueName ending in .fifo.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.queueType = AWSPSMENUMS!SqsQueueType#FIFO
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.queueName.hasText() and self.queueName.endsWith('.fifo')
```

This rule reads: `queueType`, `queueName`, `resourceLabel`.

### Diagnostic and repair

> FIFO SQS queue must have a queueName ending in .fifo. Fix: rename the queue to end with .fifo.

**How to fix it:**

rename the queue to end with .fifo.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `StandardQueueShouldNotUseFifoSuffix`

**Context:** `AWSPSM!SqsQueue`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/messaging.evl:15`

### Why this rule exists

Advises that standard queue should not use fifo suffix. This is a review signal about queue type, queue name, resource label, not a cosmetic naming preference. In this part of the model, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Standard SQS queue has a .fifo suffix. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.queueType = AWSPSMENUMS!SqsQueueType#STANDARD
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (not self.queueName.hasText()) or (not self.queueName.endsWith('.fifo'))
```

This rule reads: `queueType`, `queueName`, `resourceLabel`.

### Diagnostic and repair

> Standard SQS queue has a .fifo suffix. Fix: remove the suffix or change queueType to FIFO.

**How to fix it:**

remove the suffix or change queueType to FIFO.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `QueueTimingRangesValid`

**Context:** `AWSPSM!SqsQueue`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/messaging.evl:22`

### Why this rule exists

Checks that queue timing ranges valid. The sqs queue element owns the evidence for this decision, including delay seconds, message retention period seconds, receive message wait time seconds, visibility timeout seconds, resource label. At this level, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SQS queue has timing settings outside AWS-supported ranges.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : ((self.delaySeconds.isUndefined()) or ((self.delaySeconds >= 0) and (self.delaySeconds <= 900))) and ((self.messageRetentionPeriodSeconds.isUndefined()) or ((self.messageRetentionPeriodSeconds >= 60) and (self.messageRetentionPeriodSeconds <= 1209600))) and ((self.receiveMessageWaitTimeSeconds.isUndefined()) or ((self.receiveMessageWaitTimeSeconds >= 0) and (self.receiveMessageWaitTimeSeconds <= 20))) and ((self.visibilityTimeoutSeconds.isUndefined()) or ((self.visibilityTimeoutSeconds >= 0) and (self.visibilityTimeoutSeconds <= 43200)))
```

This rule reads: `delaySeconds`, `messageRetentionPeriodSeconds`, `receiveMessageWaitTimeSeconds`, `visibilityTimeoutSeconds`, `resourceLabel`.

### Diagnostic and repair

> SQS queue has timing settings outside AWS-supported ranges. Fix: delay 0..900s, retention 60..1209600s, wait time 0..20s, visibility 0..43200s.

**How to fix it:**

delay 0..900s, retention 60..1209600s, wait time 0..20s, visibility 0..43200s.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `QueueMessageSizeRangeValid`

**Context:** `AWSPSM!SqsQueue`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/messaging.evl:31`

### Why this rule exists

Checks that queue message size range valid. The sqs queue element owns the evidence for this decision, including maximum message size, resource label. At this level, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SQS queue has maximumMessageSize outside 1024..1048576 bytes.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.maximumMessageSize.isUndefined()) or ((self.maximumMessageSize >= 1024) and (self.maximumMessageSize <= 1048576))
```

This rule reads: `maximumMessageSize`, `resourceLabel`.

### Diagnostic and repair

> SQS queue has maximumMessageSize outside 1024..1048576 bytes. Fix: use a valid message size limit.

**How to fix it:**

use a valid message size limit.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RedrivePolicyValid`

**Context:** `AWSPSM!SqsQueue`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/messaging.evl:37`

### Why this rule exists

Checks that redrive policy valid. The sqs queue element owns the evidence for this decision, including redrive policy, queue type, resource label. At this level, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SQS queue has an invalid redrive policy.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.redrivePolicy.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.redrivePolicy.deadLetterQueue.isDefined()) and (self.redrivePolicy.deadLetterQueue <> self) and (self.redrivePolicy.maxReceiveCount >= 1) and (self.redrivePolicy.deadLetterQueue.queueType = self.queueType)
```

This rule reads: `redrivePolicy`, `queueType`, `resourceLabel`.

### Diagnostic and repair

> SQS queue has an invalid redrive policy. Fix: set a different DLQ, maxReceiveCount >= 1, and ensure source queue and DLQ have the same queueType.

**How to fix it:**

set a different DLQ, maxReceiveCount >= 1, and ensure source queue and DLQ have the same queueType.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionQueueShouldBeEncrypted`

**Context:** `AWSPSM!SqsQueue`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/messaging.evl:47`

### Why this rule exists

Advises that production queue should be encrypted. This is a review signal about is production scoped, sqs managed sse enabled, kms key, resource label, not a cosmetic naming preference. In this part of the model, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped SQS queue has no explicit encryption. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.isProductionScoped()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.sqsManagedSseEnabled = true) or self.kmsKey.isDefined()
```

This rule reads: `isProductionScoped`, `sqsManagedSseEnabled`, `kmsKey`, `resourceLabel`.

### Diagnostic and repair

> Production-scoped SQS queue has no explicit encryption. Fix: enable sqsManagedSseEnabled or attach a KMS key.

**How to fix it:**

enable sqsManagedSseEnabled or attach a KMS key.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `FifoTopicNameSuffix`

**Context:** `AWSPSM!SnsTopic`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/messaging.evl:57`

### Why this rule exists

Checks that fifo topic name suffix. The sns topic element owns the evidence for this decision, including fifo topic, topic name, resource label. At this level, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: FIFO SNS topic must have a topicName ending in .fifo.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.fifoTopic = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.topicName.hasText() and self.topicName.endsWith('.fifo')
```

This rule reads: `fifoTopic`, `topicName`, `resourceLabel`.

### Diagnostic and repair

> FIFO SNS topic must have a topicName ending in .fifo. Fix: rename the topic to end with .fifo.

**How to fix it:**

rename the topic to end with .fifo.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProductionTopicShouldBeEncrypted`

**Context:** `AWSPSM!SnsTopic`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/messaging.evl:64`

### Why this rule exists

Advises that production topic should be encrypted. This is a review signal about is production scoped, kms key, resource label, not a cosmetic naming preference. In this part of the model, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: Production-scoped SNS topic has no KMS key. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

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

> Production-scoped SNS topic has no KMS key. Fix: attach kmsKey unless an approved exception exists.

**How to fix it:**

attach kmsKey unless an approved exception exists.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `SubscriptionHasEndpointOrResource`

**Context:** `AWSPSM!SnsSubscription`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/messaging.evl:74`

### Why this rule exists

Checks that subscription has endpoint or resource. The sns subscription element owns the evidence for this decision, including endpoint, endpoint resource, resource label. At this level, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SNS subscription has neither endpoint nor endpointResource.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.endpoint.hasText() or self.endpointResource.isDefined()
```

This rule reads: `endpoint`, `endpointResource`, `resourceLabel`.

### Diagnostic and repair

> SNS subscription has neither endpoint nor endpointResource. Fix: provide an endpoint string or reference the target AwsResource.

**How to fix it:**

provide an endpoint string or reference the target AwsResource.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `FifoTopicToSqsRequiresFifoQueue`

**Context:** `AWSPSM!SnsSubscription`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/messaging.evl:80`

### Why this rule exists

Checks that fifo topic to sqs requires fifo queue. The sns subscription element owns the evidence for this decision, including topic, endpoint resource, resource label. At this level, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SNS FIFO topic subscription targets a non-FIFO SQS queue.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.topic.isDefined() and (self.topic.fifoTopic = true) and self.endpointResource.isDefined() and self.endpointResource.isKindOf(AWSPSM!SqsQueue)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.endpointResource.queueType = AWSPSMENUMS!SqsQueueType#FIFO
```

This rule reads: `topic`, `endpointResource`, `resourceLabel`.

### Diagnostic and repair

> SNS FIFO topic subscription targets a non-FIFO SQS queue. Fix: use a FIFO queue ending in .fifo or change the topic to standard.

**How to fix it:**

use a FIFO queue ending in .fifo or change the topic to standard.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternalHttpSubscriptionsShouldHaveDlq`

**Context:** `AWSPSM!SnsSubscription`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/messaging.evl:87`

### Why this rule exists

Advises that external http subscriptions should have dlq. This is a review signal about protocol, dead letter queue, resource label, not a cosmetic naming preference. In this part of the model, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: HTTP/HTTPS SNS subscription has no DLQ. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.protocol = AWSPSMENUMS!SnsProtocol#HTTP) or (self.protocol = AWSPSMENUMS!SnsProtocol#HTTPS)
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.deadLetterQueue.isDefined()
```

This rule reads: `protocol`, `deadLetterQueue`, `resourceLabel`.

### Diagnostic and repair

> HTTP/HTTPS SNS subscription has no DLQ. Fix: attach deadLetterQueue so failed deliveries can be retained and replayed.

**How to fix it:**

attach deadLetterQueue so failed deliveries can be retained and replayed.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `FilterRuleHasValues`

**Context:** `AWSPSM!SnsFilterRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/messaging.evl:97`

### Why this rule exists

Checks that filter rule has values. The sns filter rule element owns the evidence for this decision, including field path, operator, values. At this level, SQS and SNS behavior matches AWS naming, FIFO, timing, encryption, redrive, subscription, and filtering semantics; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: SNS filter rule is incomplete.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.fieldPath.hasText() and self.operator.hasText() and self.values.notEmpty()
```

This rule reads: `fieldPath`, `operator`, `values`.

### Diagnostic and repair

> SNS filter rule is incomplete. Fix: set fieldPath, operator, and at least one value.

**How to fix it:**

set fieldPath, operator, and at least one value.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
