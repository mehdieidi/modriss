# PIM → AWS PSM: Data Messaging Events

This module binds PIM data and integration abstractions to DynamoDB, S3, SQS, SNS, EventBridge, and Scheduler. It does more than rename classes: it encodes AWS key/index rules, encryption, recovery, FIFO and redrive behavior, targets, subscriptions, event patterns, and secure transport policies, with unresolved provider choices represented explicitly.

Source module: `mde/transformations/pim-to-awspsm/data-messaging-events.etl`.

## Reading this page

A transformation rule is not a validation constraint: it decides whether and how a source element contributes to the target model. Read the guard as a routing decision, the target table as the model-level result, the behavior section as the important semantic side effects, and the trace/manual-decision information as the hand-off to review and later phases.

---

## Supporting ETL operations

These operations are not independent source-to-target rules, but they materially shape the result. They derive defaults, create secondary resources, cache correspondences, or resolve relationships after the main rule has run.

| Operation                           | Role                                                                           | Source                                                            |
| ----------------------------------- | ------------------------------------------------------------------------------ | ----------------------------------------------------------------- |
| `resolveSqsRedrivePolicies`         | Resolves resolve sqs redrive policies.                                         | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:164` |
| `createSnsSubscriptionDlq`          | Creates create sns subscription dlq.                                           | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:253` |
| `addDynamoKeys`                     | Adds or records add dynamo keys.                                               | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:344` |
| `addDynamoAttribute`                | Adds or records add dynamo attribute.                                          | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:382` |
| `dynamoKey`                         | Computes dynamo key.                                                           | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:395` |
| `dynamoType`                        | Computes dynamo type.                                                          | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:405` |
| `addDynamoIndexes`                  | Adds or records add dynamo indexes.                                            | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:416` |
| `fieldByStorageOrName`              | Computes field by storage or name.                                             | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:468` |
| `indexKeyFields`                    | Computes index key fields.                                                     | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:487` |
| `normalizedLookupKey`               | Converts the receiver to normalized lookup key.                                | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:502` |
| `createS3Encryption`                | Creates create s3 encryption.                                                  | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:507` |
| `createStrictPublicAccessBlock`     | Creates create strict public access block.                                     | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:517` |
| `createS3Ownership`                 | Creates create s3 ownership.                                                   | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:529` |
| `createS3Lifecycle`                 | Creates create s3 lifecycle.                                                   | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:542` |
| `retentionDays`                     | Computes retention days.                                                       | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:563` |
| `extractLeadingInteger`             | Computes extract leading integer.                                              | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:588` |
| `createS3Notification`              | Creates create s3 notification.                                                | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:608` |
| `eventTypesPatternJson`             | Computes event types pattern json.                                             | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:623` |
| `createEventBridgeTarget`           | Creates create event bridge target.                                            | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:638` |
| `defaultEventBridgeRetryPolicy`     | Creates default event bridge retry policy.                                     | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:675` |
| `endpointResource`                  | Computes endpoint resource.                                                    | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:685` |
| `externalEndpointForSubscription`   | Computes external endpoint for subscription.                                   | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:697` |
| `unresolvedTargetArn`               | Computes unresolved target arn.                                                | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:705` |
| `subscriptionProtocol`              | Computes subscription protocol.                                                | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:710` |
| `eventBridgeTargetKind`             | Computes event bridge target kind.                                             | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:725` |
| `createSchedulerRole`               | Creates create scheduler role.                                                 | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:755` |
| `createUnresolvedScheduleTarget`    | Creates create unresolved schedule target.                                     | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:766` |
| `createStorageResourcePolicy`       | Creates create storage resource policy.                                        | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:779` |
| `createDenyInsecureTransportPolicy` | Creates create deny insecure transport policy.                                 | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:800` |
| `addWildcardPrincipal`              | Adds the wildcard principal required by a deny-only resource policy statement. | `mde/transformations/pim-to-awspsm/data-messaging-events.etl:822` |

---

## `DataStore2DynamoDbTable`

**Source:** `ds` to `DATA!DataStore`  
**Target:** `t` to `AWSPSMSTORAGE!DynamoDbTable`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:2`

### Why this rule exists

Key-value, document, and cache-shaped PIM stores are bound to DynamoDB with keys, indexes, streams, encryption, backup/PITR, production deletion protection, and data-protection policy. The mapping encodes a deployable baseline while preserving provider-specific review points.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : ds.storeKind = PIMTYPES!StoreKind#KEY_VALUE or ds.storeKind = PIMTYPES!StoreKind#DOCUMENT or ds.storeKind = PIMTYPES!StoreKind#CACHE
```

### What it creates

- `t` (`AWSPSMSTORAGE!DynamoDbTable`): Generated dynamo db table (t).
- Secondary objects created in the rule body: `AWSPSMSTORAGE!DynamoDbStreamSpecification`, `AWSPSMSTORAGE!DynamoDbSseSpecification`, `AWSPSMSTORAGE!DynamoDbBackupPolicy`.

### Important behavior encoded in the rule

The rule directly assigns: `t.id`, `t.tableName`, `t.billingMode`, `t.tableClass`, `t.deletionProtectionEnabled`, `t.pointInTimeRecoveryEnabled`, `t.contributorInsightsEnabled`, `t.importSourceSpecificationRequired`, `stream.id`, `stream.name`, `stream.streamViewType`, `t.streamSpecification`, `sse.id`, `sse.name`, `sse.enabled`, `sse.sseType` ….

### How to troubleshoot or repair it

Verify the data store instance first: the guard shown above must evaluate to true for this rule to run. If it should run but does not, check the guarded links, enum values, and earlier transformation outputs; if it is intentionally out of scope, use the trace/readiness report to record that decision rather than adding a dummy target.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:2`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `DataStore2NativeUnsupportedStore`

**Source:** `ds` to `DATA!DataStore`  
**Target:** `n` to `AWSPSMCORE!AwsNativeResource`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:50`

### Why this rule exists

Unsupported PIM store kinds are represented by a CloudFormation custom-resource placeholder and a blocking service-selection decision. This is safer than guessing a database because storage semantics such as transactions, indexes, retention, and cost cannot be recovered from a generic kind alone.

### When the rule runs

The rule is conditional. It runs only when this guard is true; a false guard means the source element belongs to another refinement path or requires a different provider mapping.

```etl
guard : not (ds.storeKind = PIMTYPES!StoreKind#KEY_VALUE or ds.storeKind = PIMTYPES!StoreKind#DOCUMENT or ds.storeKind = PIMTYPES!StoreKind#CACHE)
```

### What it creates

- `n` (`AWSPSMCORE!AwsNativeResource`): Review-required placeholder for an unsupported mapping.

### Important behavior encoded in the rule

The rule directly assigns: `n.id`, `n.cloudFormationType`.
Manual decisions raised by this rule: `DATASTORE_SERVICE_SELECTION_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `DATASTORE_SERVICE_SELECTION_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the data store actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:50`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `ObjectStore2S3Bucket`

**Source:** `os` to `DATA!ObjectStore`  
**Target:** `b` to `AWSPSMSTORAGE!S3Bucket`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:69`

### Why this rule exists

An object store becomes an S3 bucket with strict public blocking, ownership controls, encryption, lifecycle, notification, versioning, and production transport policy. The rule treats object storage as a durable security boundary rather than a generic file container.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `b` (`AWSPSMSTORAGE!S3Bucket`): Generated s3 bucket (b).
- Secondary objects created in the rule body: `AWSPSMSTORAGE!S3BucketPolicy`.

### Important behavior encoded in the rule

The rule directly assigns: `b.id`, `b.bucketName`, `b.versioningStatus`, `b.objectLockEnabled`, `b.transferAccelerationEnabled`, `b.eventBridgeNotificationEnabled`, `b.publicAccessMode`, `b.bucketKeyEnabled`, `b.accessControl`, `b.encryption`, `b.publicAccessBlock`, `b.ownershipControls`, `b.lifecycle`, `b.notificationConfiguration`, `bp.id`, `bp.bucket` ….

### How to troubleshoot or repair it

Verify that the object store is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:69`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Queue2SqsQueue`

**Source:** `q` to `INTEGRATION!Queue`  
**Target:** `sqs` to `AWSPSMMESSAGING!SqsQueue`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:110`

### Why this rule exists

A PIM queue becomes SQS with FIFO naming/type, deduplication, timing, encryption, retention, visibility, and production transport policy. Missing DLQ or FIFO ordering intent becomes an explicit manual decision instead of an unsafe default.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `sqs` (`AWSPSMMESSAGING!SqsQueue`): Generated sqs queue (sqs).
- Secondary objects created in the rule body: `AWSPSMMESSAGING!SqsQueuePolicy`.

### Important behavior encoded in the rule

The rule directly assigns: `sqs.id`, `sqs.queueName`, `sqs.queueType`, `sqs.contentBasedDeduplication`, `sqs.delaySeconds`, `sqs.maximumMessageSize`, `sqs.messageRetentionPeriodSeconds`, `sqs.receiveMessageWaitTimeSeconds`, `sqs.visibilityTimeoutSeconds`, `sqs.kmsDataKeyReusePeriodSeconds`, `sqs.sqsManagedSseEnabled`, `qp.id`, `qp.policyDocument`.
Manual decisions raised by this rule: `SQS_DLQ_REQUIRED`, `FIFO_ORDERING_KEY_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `SQS_DLQ_REQUIRED, FIFO_ORDERING_KEY_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the queue actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:110`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Topic2SnsTopic`

**Source:** `t` to `INTEGRATION!Topic`  
**Target:** `sns` to `AWSPSMMESSAGING!SnsTopic`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:186`

### Why this rule exists

A PIM topic becomes SNS, including FIFO semantics, deduplication, display name, and a production deny-insecure-transport policy. Subscriber resources are deliberately handled separately so one topic can be connected to multiple concrete endpoints.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `sns` (`AWSPSMMESSAGING!SnsTopic`): Generated sns topic (sns).
- Secondary objects created in the rule body: `AWSPSMMESSAGING!SnsTopicPolicy`.

### Important behavior encoded in the rule

The rule directly assigns: `sns.id`, `sns.topicName`, `sns.fifoTopic`, `sns.contentBasedDeduplication`, `sns.displayName`, `tp.id`, `tp.policyDocument`.

### How to troubleshoot or repair it

Verify that the topic is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:186`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Subscription2SnsSubscription`

**Source:** `s` to `INTEGRATION!Subscription`  
**Target:** `sub` to `AWSPSMMESSAGING!SnsSubscription`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:212`

### Why this rule exists

A subscription becomes an SNS subscription whose protocol and endpoint are resolved to AWS resources when possible or retained as an external endpoint otherwise. The rule also creates Lambda permissions, caches identity for later relationship resolution, and surfaces DLQ needs.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `sub` (`AWSPSMMESSAGING!SnsSubscription`): Generated sns subscription (sub).

### Important behavior encoded in the rule

The rule directly assigns: `sub.id`, `sub.topic`, `sub.protocol`, `sub.endpointResource`, `sub.endpoint`, `sub.filterPolicyScope`, `sub.rawMessageDelivery`, `sub.deliveryPolicyJson`, `sub.deadLetterQueue`.
Manual decisions raised by this rule: `SNS_SUBSCRIPTION_DLQ_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `SNS_SUBSCRIPTION_DLQ_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the subscription actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:212`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `EventBus2EventBridgeBus`

**Source:** `b` to `INTEGRATION!EventBus`  
**Target:** `eb` to `AWSPSMEVENTS!EventBridgeBus`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:270`

### Why this rule exists

An abstract event bus becomes an EventBridge bus with a stable physical name and source namespace. The mapping establishes the event partition before routing rules and targets are attached.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `eb` (`AWSPSMEVENTS!EventBridgeBus`): Generated event bridge bus (eb).

### Important behavior encoded in the rule

The rule directly assigns: `eb.id`, `eb.busName`, `eb.eventSourceName`.

### How to troubleshoot or repair it

Verify that the event bus is present and semantically complete, then rerun the transformation. If the target is missing or incomplete, inspect the source attributes named in the rule body, the referenced helper operation, and the post-phase that resolves its relationships before changing the ETL itself.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:270`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `EventRoutingRule2EventBridgeRule`

**Source:** `rr` to `INTEGRATION!EventRoutingRule`  
**Target:** `er` to `AWSPSMEVENTS!EventBridgeRule`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:282`

### Why this rule exists

An event-routing rule becomes an EventBridge rule with pattern/schedule, enabled state, bus, and concrete targets. If the PIM rule contains neither usable event pattern nor schedule, the transformation creates a blocking decision rather than emitting an inert AWS rule.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `er` (`AWSPSMEVENTS!EventBridgeRule`): Generated event bridge rule (er).

### Important behavior encoded in the rule

The rule directly assigns: `er.id`, `er.ruleName`, `er.descriptionText`, `er.eventPatternJson`, `er.scheduleExpression`, `er.state`, `er.bus`.
Manual decisions raised by this rule: `EVENT_PATTERN_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `EVENT_PATTERN_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the event routing rule actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:282`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---

## `Schedule2EventBridgeSchedule`

**Source:** `s` to `INTEGRATION!Schedule`  
**Target:** `es` to `AWSPSMEVENTS!EventBridgeSchedule`  
**Source location:** `mde/transformations/pim-to-awspsm/data-messaging-events.etl:312`

### Why this rule exists

A PIM schedule becomes an EventBridge Scheduler schedule with timezone, execution role, state, and exactly one target. Multiple or missing targets are intentionally unresolved because a schedule with ambiguous delivery semantics is not safe to deploy.

### When the rule runs

There is no explicit guard, so every source instance of the declared type is eligible for this mapping. Eligibility does not guarantee that every optional relationship or downstream target can be resolved.

### What it creates

- `es` (`AWSPSMEVENTS!EventBridgeSchedule`): Generated event bridge schedule (es).

### Important behavior encoded in the rule

The rule directly assigns: `es.id`, `es.scheduleName`, `es.scheduleExpression`, `es.scheduleExpressionTimezone`, `es.flexibleTimeWindowJson`, `es.state`, `es.descriptionText`, `es.role`, `es.target`.
Manual decisions raised by this rule: `SCHEDULE_TARGET_REQUIRED`. These are intentional hand-off points, not transformation failures; resolve them in the model review/readiness workflow.

### How to troubleshoot or repair it

Start with the manual decision(s) `SCHEDULE_TARGET_REQUIRED` and complete the requested provider or business choice. Then re-run the transformation and validate the resulting target. If the rule did not produce an object, inspect whether the schedule actually satisfies its guard and whether the required upstream correspondence exists.

### Authoritative source

Read the complete ETL rule at `mde/transformations/pim-to-awspsm/data-messaging-events.etl:312`. The documentation summarizes its purpose and observable effects; the ETL body remains the authority for exact assignments and helper calls.

---
