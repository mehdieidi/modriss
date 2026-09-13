# PIM validation: Integration

Integration rules ensure channels, queues, topics, schedules, subscriptions, buses, and flows say enough about delivery, routing, ordering, replay, dead letters, and ownership to be mapped safely to AWS messaging and event services.

Source profile: `mde/validation/pim/rules/integration.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `EventTypeHasSchema`

**Context:** `PIM!EventType`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:8`

### Why this rule exists

The rule checks whether event type has schema. The event type element provides the relevant evidence through schema, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-EVENT-001] EventType ' ' has no schema.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.schema.isDefined()
```

This rule reads: `schema`, `displayName`.

### Diagnostic and repair

> [PIM-EVENT-001] EventType ' ' has no schema. Fix: attach a Schema with schemaKind EVENT or MESSAGE.

**How to fix it:**

attach a Schema with schemaKind EVENT or MESSAGE.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PersonalDataEventSchemaIsClassified`

**Context:** `PIM!EventType`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:14`

### Why this rule exists

The rule checks whether personal data event schema is classified. The event type element provides the relevant evidence through carries personal data, schema, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-EVENT-002] EventType ' ' carries personal/sensitive data but its schema fields are not fully classified.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.carriesPersonalData()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.schema.isDefined() and self.schema.hasClassifiedSensitiveFields()
```

This rule reads: `carriesPersonalData`, `schema`, `displayName`.

### Diagnostic and repair

> [PIM-EVENT-002] EventType ' ' carries personal/sensitive data but its schema fields are not fully classified. Fix: classify every sensitive field and attach appropriate data protection policies.

**How to fix it:**

classify every sensitive field and attach appropriate data protection policies.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EventTypeShouldHaveVersionAndSource`

**Context:** `PIM!EventType`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:21`

### Why this rule exists

The rule checks whether event type should have version and source. It examines version, source domain, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-EVENT-003] EventType ' ' should have version and sourceDomain. Suggested fix: set a semantic event version and the domain/service that owns the event. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.version.hasText() and self.sourceDomain.hasText()
```

This rule reads: `version`, `sourceDomain`, `displayName`.

### Diagnostic and repair

> [PIM-EVENT-003] EventType ' ' should have version and sourceDomain. Suggested fix: set a semantic event version and the domain/service that owns the event.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ExternalOrReplayableEventShouldHaveEnvelope`

**Context:** `PIM!EventType`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:27`

### Why this rule exists

The rule checks whether external or replayable event should have envelope. It examines external event, replayable, audit event, envelope, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-EVENT-004] EventType ' ' is external, replayable or audit-related but has no EventEnvelope. Suggested fix: define envelope fields for eventId, eventType, source, time, version and correlation/causation IDs. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.externalEvent.isTrue() or self.replayable.isTrue() or self.auditEvent.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.envelope.isDefined()
```

This rule reads: `externalEvent`, `replayable`, `auditEvent`, `envelope`, `displayName`.

### Diagnostic and repair

> [PIM-EVENT-004] EventType ' ' is external, replayable or audit-related but has no EventEnvelope. Suggested fix: define envelope fields for eventId, eventType, source, time, version and correlation/causation IDs.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `EventTypeProducerConsumerLinksConsistent`

**Context:** `PIM!EventType`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:34`

### Why this rule exists

The rule checks whether event type producer consumer links consistent. It examines produced by, participant key, consumed by, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-EVENT-006] EventType ' ' has producedBy/consumedBy links that are not reflected by event-channel producer/consumer links. Suggested fix: synchronize EventType and EventChannel relationship references. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.producedBy.forAll(fn | eventChannelProducerKeys().includes(self.participantKey(fn))) and self.consumedBy.forAll(fn | eventChannelConsumerKeys().includes(self.participantKey(fn)))
```

This rule reads: `producedBy`, `participantKey`, `consumedBy`, `displayName`.

### Diagnostic and repair

> [PIM-EVENT-006] EventType ' ' has producedBy/consumedBy links that are not reflected by event-channel producer/consumer links. Suggested fix: synchronize EventType and EventChannel relationship references.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `EnvelopeShouldCarryCorrelationFields`

**Context:** `PIM!EventEnvelope`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:45`

### Why this rule exists

The rule checks whether envelope should carry correlation fields. It examines event id field, event type field, source field, time field, version field. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-EVENT-005] EventEnvelope ' ' lacks one or more standard envelope fields. Suggested fix: define eventIdField, eventTypeField, sourceField, timeField, versionField and correlationIdField. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.eventIdField.hasText() and self.eventTypeField.hasText() and self.sourceField.hasText() and self.timeField.hasText() and self.versionField.hasText() and self.correlationIdField.hasText()
```

This rule reads: `eventIdField`, `eventTypeField`, `sourceField`, `timeField`, `versionField`, `correlationIdField`, `displayName`.

### Diagnostic and repair

> [PIM-EVENT-005] EventEnvelope ' ' lacks one or more standard envelope fields. Suggested fix: define eventIdField, eventTypeField, sourceField, timeField, versionField and correlationIdField.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ChannelHasEventTypes`

**Context:** `PIM!EventChannel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:56`

### Why this rule exists

The rule checks whether channel has event types. The event channel element provides the relevant evidence through event types, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-CHAN-001] Event channel ' ' has no eventTypes.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.eventTypes.notEmpty()
```

This rule reads: `eventTypes`, `displayName`.

### Diagnostic and repair

> [PIM-CHAN-001] Event channel ' ' has no eventTypes. Fix: attach the event types/messages that can flow through this channel.

**How to fix it:**

attach the event types/messages that can flow through this channel.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PersonalDataChannelMustBeEncrypted`

**Context:** `PIM!EventChannel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:62`

### Why this rule exists

The rule checks whether personal data channel must be encrypted. The event channel element provides the relevant evidence through carries personal data, encrypted, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-CHAN-002] Event channel ' ' carries personal/sensitive events but is not encrypted.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.carriesPersonalData()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.encrypted.isTrue()
```

This rule reads: `carriesPersonalData`, `encrypted`, `displayName`.

### Diagnostic and repair

> [PIM-CHAN-002] Event channel ' ' carries personal/sensitive events but is not encrypted. Fix: set encrypted to true and attach relevant data protection/security policies.

**How to fix it:**

set encrypted to true and attach relevant data protection/security policies.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExactlyOnceRequiresIdempotentConsumers`

**Context:** `PIM!EventChannel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:69`

### Why this rule exists

The rule checks whether exactly once requires idempotent consumers. The event channel element provides the relevant evidence through delivery semantics, consumers, idempotency, workflow consumers, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-CHAN-003] Channel ' ' requires exactly/effectively-once delivery but not all consumers have idempotency policies.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.deliverySemantics.enumIs("EXACTLY_ONCE_REQUIRED") or self.deliverySemantics.enumIs("EFFECTIVELY_ONCE_WITH_IDEMPOTENCY")
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.consumers.forAll(functionItem | functionItem.idempotency.isDefined() and functionItem.idempotency.keySource.hasText()) and self.workflowConsumers.forAll(flowItem | flowItem.idempotency.isDefined() and flowItem.idempotency.keySource.hasText())
```

This rule reads: `deliverySemantics`, `consumers`, `idempotency`, `workflowConsumers`, `displayName`.

### Diagnostic and repair

> [PIM-CHAN-003] Channel ' ' requires exactly/effectively-once delivery but not all consumers have idempotency policies. Fix: attach IdempotencyPolicy with keySource to every consumer function/workflow.

**How to fix it:**

attach IdempotencyPolicy with keySource to every consumer function/workflow.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `OrderedChannelHasOrderingKey`

**Context:** `PIM!EventChannel`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:77`

### Why this rule exists

The rule checks whether ordered channel has ordering key. The event channel element provides the relevant evidence through ordering requirement, partition key expression, event types, ordering key, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-CHAN-004] Ordered channel ' ' has no partitionKeyExpression or event orderingKey.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.orderingRequirement.enumIs("PER_KEY") or self.orderingRequirement.enumIs("GLOBAL")
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.partitionKeyExpression.hasText() or self.eventTypes.exists(eventItem | eventItem.orderingKey.hasText())
```

This rule reads: `orderingRequirement`, `partitionKeyExpression`, `eventTypes`, `orderingKey`, `displayName`.

### Diagnostic and repair

> [PIM-CHAN-004] Ordered channel ' ' has no partitionKeyExpression or event orderingKey. Fix: define how messages are ordered without naming a provider-specific mechanism.

**How to fix it:**

define how messages are ordered without naming a provider-specific mechanism.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ChannelShouldHaveProducerAndConsumerIntent`

**Context:** `PIM!EventChannel`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:84`

### Why this rule exists

The rule checks whether channel should have producer and consumer intent. It examines producers, external producers, consumers, workflow consumers, external consumers. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-CHAN-005] Channel ' ' has no producer/consumer intent. Suggested fix: connect producers and consumers or mark it as intentionally reserved in rationale. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.producers.notEmpty() or self.externalProducers.notEmpty() or self.consumers.notEmpty() or self.workflowConsumers.notEmpty() or self.externalConsumers.notEmpty() or self.rationale.hasText()
```

This rule reads: `producers`, `externalProducers`, `consumers`, `workflowConsumers`, `externalConsumers`, `rationale`, `displayName`.

### Diagnostic and repair

> [PIM-CHAN-005] Channel ' ' has no producer/consumer intent. Suggested fix: connect producers and consumers or mark it as intentionally reserved in rationale.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ChannelProducerConsumerLinksConsistent`

**Context:** `PIM!EventChannel`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:91`

### Why this rule exists

The rule checks whether channel producer consumer links consistent. It examines producers, event types, produced by, consumers, consumed by. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-CHAN-006] Channel ' ' has producer/consumer links that are not reflected by event types, functions, external adapters, workflows, or event flows. Suggested fix: keep EventChannel, EventType, Function and EventFlow references synchronized. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.producers.forAll(fn | self.eventTypes.exists(eventItem | eventItem.producedBy.includes(fn) or fn.publishes.includes(eventItem))) and self.consumers.forAll(fn | self.eventTypes.exists(eventItem | eventItem.consumedBy.includes(fn) or fn.subscribesTo.includes(eventItem))) and self.externalProducers.forAll(adapter | self.eventTypes.exists(eventItem | eventItem.producedBy.includes(adapter))) and self.externalConsumers.forAll(adapter | self.eventTypes.exists(eventItem | eventItem.consumedBy.includes(adapter))) and self.workflowConsumers.forAll(workflowItem | eventFlowWorkflowConsumerKeys().includes(self.workflowConsumerKey(workflowItem)))
```

This rule reads: `producers`, `eventTypes`, `producedBy`, `consumers`, `consumedBy`, `externalProducers`, `externalConsumers`, `workflowConsumers`, `workflowConsumerKey`, `displayName`.

### Diagnostic and repair

> [PIM-CHAN-006] Channel ' ' has producer/consumer links that are not reflected by event types, functions, external adapters, workflows, or event flows. Suggested fix: keep EventChannel, EventType, Function and EventFlow references synchronized.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RetryQueueNeedsDeadLetterChannel`

**Context:** `PIM!Queue`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:105`

### Why this rule exists

The rule checks whether retry queue needs dead letter channel. The queue element provides the relevant evidence through max receive attempts, dead letter channel, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-QUEUE-001] Queue ' ' retries messages but has no deadLetterChannel.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.maxReceiveAttempts.isDefined() and self.maxReceiveAttempts > 1
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.deadLetterChannel.isDefined()
```

This rule reads: `maxReceiveAttempts`, `deadLetterChannel`, `displayName`.

### Diagnostic and repair

> [PIM-QUEUE-001] Queue ' ' retries messages but has no deadLetterChannel. Fix: attach a DLQ queue or set maxReceiveAttempts to 1 if no retry/DLQ behavior is intended.

**How to fix it:**

attach a DLQ queue or set maxReceiveAttempts to 1 if no retry/DLQ behavior is intended.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `BatchQueueNeedsPartialFailureDecision`

**Context:** `PIM!Queue`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:112`

### Why this rule exists

The rule checks whether batch queue needs partial failure decision. The queue element provides the relevant evidence through batch policy, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-QUEUE-002] Queue ' ' has a BatchPolicy but partialFailureHandling is UNDECIDED.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.batchPolicy.isDefined()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : not self.batchPolicy.partialFailureHandling.enumIs("UNDECIDED")
```

This rule reads: `batchPolicy`, `displayName`.

### Diagnostic and repair

> [PIM-QUEUE-002] Queue ' ' has a BatchPolicy but partialFailureHandling is UNDECIDED. Fix: set partialFailureHandling to REQUIRED or NOT_REQUIRED and record the decisionRationale.

**How to fix it:**

set partialFailureHandling to REQUIRED or NOT_REQUIRED and record the decisionRationale.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `FifoQueueNeedsDeduplicationOrIdempotency`

**Context:** `PIM!Queue`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:119`

### Why this rule exists

The rule checks whether fifo queue needs deduplication or idempotency. The queue element provides the relevant evidence through fifo required, deduplication required, consumers, idempotency, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-QUEUE-003] FIFO queue ' ' needs deduplication or idempotent consumers.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.fifoRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.deduplicationRequired.isTrue() or self.consumers.forAll(functionItem | functionItem.idempotency.isDefined())
```

This rule reads: `fifoRequired`, `deduplicationRequired`, `consumers`, `idempotency`, `displayName`.

### Diagnostic and repair

> [PIM-QUEUE-003] FIFO queue ' ' needs deduplication or idempotent consumers. Fix: set deduplicationRequired to true or attach idempotency policies to all consumers.

**How to fix it:**

set deduplicationRequired to true or attach idempotency policies to all consumers.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `QueueShouldHaveVisibilityTimeout`

**Context:** `PIM!Queue`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:126`

### Why this rule exists

The rule checks whether queue should have visibility timeout. It examines visibility timeout seconds, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-QUEUE-004] Queue ' ' has no positive visibilityTimeoutSeconds. Suggested fix: set a timeout that exceeds normal consumer processing time and retry behavior. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.visibilityTimeoutSeconds.isDefined() and self.visibilityTimeoutSeconds > 0
```

This rule reads: `visibilityTimeoutSeconds`, `displayName`.

### Diagnostic and repair

> [PIM-QUEUE-004] Queue ' ' has no positive visibilityTimeoutSeconds. Suggested fix: set a timeout that exceeds normal consumer processing time and retry behavior.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `FilteringTopicShouldUseSubscriptionFilters`

**Context:** `PIM!Topic`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:136`

### Why this rule exists

The rule checks whether filtering topic should use subscription filters. It examines filtering required, subscriptions, filter expression, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-TOPIC-001] Topic ' ' requires filtering but none of its subscriptions define filterExpression. Suggested fix: add subscription filters or mark filteringRequired false. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.filteringRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.subscriptions.exists(subscriptionItem | subscriptionItem.filterExpression.hasText())
```

This rule reads: `filteringRequired`, `subscriptions`, `filterExpression`, `displayName`.

### Diagnostic and repair

> [PIM-TOPIC-001] Topic ' ' requires filtering but none of its subscriptions define filterExpression. Suggested fix: add subscription filters or mark filteringRequired false.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `EventBusShouldHaveRoutingRules`

**Context:** `PIM!EventBus`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:147`

### Why this rule exists

The rule checks whether event bus should have routing rules. It examines routing rules, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-BUS-001] EventBus ' ' has no routingRules. Suggested fix: add EventRoutingRule entries or use a simpler channel type if no routing is needed. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.routingRules.notEmpty()
```

This rule reads: `routingRules`, `displayName`.

### Diagnostic and repair

> [PIM-BUS-001] EventBus ' ' has no routingRules. Suggested fix: add EventRoutingRule entries or use a simpler channel type if no routing is needed.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `RoutingRuleHasPatternOrSchedule`

**Context:** `PIM!EventRoutingRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:157`

### Why this rule exists

The rule checks whether routing rule has pattern or schedule. The event routing rule element provides the relevant evidence through event pattern, schedule expression, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-BUS-002] EventRoutingRule ' ' has neither eventPattern nor scheduleExpression.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.eventPattern.hasText() or self.scheduleExpression.hasText()
```

This rule reads: `eventPattern`, `scheduleExpression`, `displayName`.

### Diagnostic and repair

> [PIM-BUS-002] EventRoutingRule ' ' has neither eventPattern nor scheduleExpression. Fix: define the event pattern or schedule that activates the rule.

**How to fix it:**

define the event pattern or schedule that activates the rule.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `EnabledRoutingRuleHasTargets`

**Context:** `PIM!EventRoutingRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:163`

### Why this rule exists

The rule checks whether enabled routing rule has targets. The event routing rule element provides the relevant evidence through enabled, targets, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-BUS-003] Enabled EventRoutingRule ' ' has no targets.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.enabled.isUndefined() or self.enabled.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.targets.notEmpty()
```

This rule reads: `enabled`, `targets`, `displayName`.

### Diagnostic and repair

> [PIM-BUS-003] Enabled EventRoutingRule ' ' has no targets. Fix: add at least one routing target such as function, workflow, adapter, channel or store.

**How to fix it:**

add at least one routing target such as function, workflow, adapter, channel or store.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DeadLetterSubscriptionShouldExplainHandling`

**Context:** `PIM!Subscription`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:174`

### Why this rule exists

The rule checks whether dead letter subscription should explain handling. It examines dead letter required, filter expression, rationale, review notes, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-SUB-001] Subscription ' ' requires dead-letter behavior but does not document filtering/error-handling intent. Suggested fix: add filterExpression, rationale or reviewNotes explaining poison message handling. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.deadLetterRequired.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.filterExpression.hasText() or self.rationale.hasText() or self.reviewNotes.hasText()
```

This rule reads: `deadLetterRequired`, `filterExpression`, `rationale`, `reviewNotes`, `displayName`.

### Diagnostic and repair

> [PIM-SUB-001] Subscription ' ' requires dead-letter behavior but does not document filtering/error-handling intent. Suggested fix: add filterExpression, rationale or reviewNotes explaining poison message handling.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ScheduleExpressionRequired`

**Context:** `PIM!Schedule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:185`

### Why this rule exists

The rule checks whether schedule expression required. The schedule element provides the relevant evidence through schedule expression, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-SCHED-001] Schedule ' ' has no scheduleExpression.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.scheduleExpression.hasText()
```

This rule reads: `scheduleExpression`, `displayName`.

### Diagnostic and repair

> [PIM-SCHED-001] Schedule ' ' has no scheduleExpression. Fix: provide a portable cron/rate expression.

**How to fix it:**

provide a portable cron/rate expression.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ScheduleShouldDeclareTimezone`

**Context:** `PIM!Schedule`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:191`

### Why this rule exists

The rule checks whether schedule should declare timezone. It examines time zone, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-SCHED-002] Schedule ' ' has no timeZone. Suggested fix: set an IANA timezone or document why UTC/default timezone is intended. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.timeZone.hasText()
```

This rule reads: `timeZone`, `displayName`.

### Diagnostic and repair

> [PIM-SCHED-002] Schedule ' ' has no timeZone. Suggested fix: set an IANA timezone or document why UTC/default timezone is intended.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `EnabledScheduleHasSingleTarget`

**Context:** `PIM!Schedule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:197`

### Why this rule exists

The rule checks whether enabled schedule has single target. The schedule element provides the relevant evidence through enabled, targets, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-SCHED-003] Enabled Schedule ' ' must have exactly one target.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.enabled.isUndefined() or self.enabled.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.targets.size() = 1
```

This rule reads: `enabled`, `targets`, `displayName`.

### Diagnostic and repair

> [PIM-SCHED-003] Enabled Schedule ' ' must have exactly one target. Fix: add one Function, Workflow, adapter, channel, or other RoutingTarget to Schedule.targets.

**How to fix it:**

add one Function, Workflow, adapter, channel, or other RoutingTarget to Schedule.targets.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `FlowPurposeRequired`

**Context:** `PIM!Flow`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/integration.evl:208`

### Why this rule exists

The rule checks whether flow purpose required. The flow element provides the relevant evidence through flow purpose, display name. At this level, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: [PIM-FLOW-001] Flow ' ' has no flowPurpose.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.flowPurpose.hasText()
```

This rule reads: `flowPurpose`, `displayName`.

### Diagnostic and repair

> [PIM-FLOW-001] Flow ' ' has no flowPurpose. Fix: describe what business or integration behavior this flow represents.

**How to fix it:**

describe what business or integration behavior this flow represents.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `CriticalFlowNeedsResilienceAndObservability`

**Context:** `PIM!Flow`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:214`

### Why this rule exists

The rule checks whether critical flow needs resilience and observability. It examines critical path, resilience, observability, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-FLOW-002] Critical flow ' ' lacks resilience or observability. Suggested fix: attach retry/timeout/dead-letter decisions and observability configuration. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.criticalPath.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.resilience.isDefined() and self.observability.isDefined()
```

This rule reads: `criticalPath`, `resilience`, `observability`, `displayName`.

### Diagnostic and repair

> [PIM-FLOW-002] Critical flow ' ' lacks resilience or observability. Suggested fix: attach retry/timeout/dead-letter decisions and observability configuration.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PersonalDataFlowNeedsPolicy`

**Context:** `PIM!Flow`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/integration.evl:221`

### Why this rule exists

The rule checks whether personal data flow needs policy. It examines contains personal data, policies, is kind of, display name. Within this part of the model, delivery, routing, ordering, replay, dead-letter, and ownership choices can be mapped to concrete channels safely. The gap is [PIM-FLOW-003] Flow ' ' contains personal data but has no DataProtectionPolicy. Suggested fix: attach data protection policy covering encryption, masking, auditing, retention and residency. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.containsPersonalData.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.policies.exists(policyItem | policyItem.isKindOf(PIM!DataProtectionPolicy))
```

This rule reads: `containsPersonalData`, `policies`, `isKindOf`, `displayName`.

### Diagnostic and repair

> [PIM-FLOW-003] Flow ' ' contains personal data but has no DataProtectionPolicy. Suggested fix: attach data protection policy covering encryption, masking, auditing, retention and residency.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
