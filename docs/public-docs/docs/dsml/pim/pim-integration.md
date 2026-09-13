# Channels, flows, and integrations

Integration concepts describe asynchronous channels, schedules, subscriptions, and flow intent independently of AWS resource names.

Source: `mde/metamodels/pim/pim-integration.emf`.

## `IntegrationElement`

An abstract integration element concept. Use one of its concrete subtypes when creating a model instance; the shared attributes and relationships defined here still apply.

Direct supertypes: `TraceableElement`, `FlowEndpoint`, `PolicyTarget`, `ProtectedResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                                  |
| -------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `integrationPurpose` | `String` [1]          | For a integration element, the model records the human explanation of integration purpose. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Reserve inventory for a confirmed order.`. |

### Relationships

This class declares no direct relationships.

## `EventChannel`

An abstract event channel concept. Use one of its concrete subtypes when creating a model instance; the shared attributes and relationships defined here still apply.

Direct supertypes: `IntegrationElement`, `DeployableElement`, `InvocationSource`, `SubscriptionTarget`, `RoutingTarget`, `EventCarrier`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                | Type and multiplicity     | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                                             |
| ------------------------ | ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `partitionKeyExpression` | `String` [1]              | Stores the expression or rule that governs partition key expression on the event channel. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `OrderedChannelHasOrderingKey` (ordered channel has ordering key) in `mde/validation/pim/rules/integration.evl` the value must be present and non-blank.                                                                                                                                                      | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`.                 |
| `encrypted`              | `Boolean` [1]             | Records whether encrypted applies to event channel. It preserves an explicit architectural or governance decision through review and transformation, so later steps do not have to infer it. Semantic validation: `ChannelHasEventTypes` (channel has event types) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition. `PersonalDataChannelMustBeEncrypted` (personal data channel must be encrypted) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition. | Either `true` or `false`. Example: `false`.                                                                                             |
| `replayRequired`         | `Boolean` [1]             | Records whether replay required applies to event channel. It preserves an explicit architectural or governance decision through review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                                                                                                                             | Either `true` or `false`. Example: `true`.                                                                                              |
| `deadLetterRequired`     | `Boolean` [1]             | Records whether dead letter required applies to event channel. The field records an explicit architectural or governance decision as an explicit, reviewable input. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                                                                                                                                                            | Either `true` or `false`. Example: `true`.                                                                                              |
| `channelKind`            | `ChannelKind` [1]         | Stores the controlled classification or strategy represented by channel kind on the event channel. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                                                                                  | Exactly one of: `QUEUE`, `TOPIC`, `EVENT_BUS`, `STREAM`, `WEBHOOK`. Example: `QUEUE`.                                                   |
| `orderingRequirement`    | `OrderingRequirement` [1] | Stores the precedence value for ordering requirement on the event channel. The field records deterministic ordering or precedence as an explicit, reviewable input. Semantic validation: `OrderedChannelHasOrderingKey` (ordered channel has ordering key) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition.                                                                                                                                                                                                   | Exactly one of: `NONE`, `PER_KEY`, `GLOBAL`. Example: `NONE`.                                                                           |
| `deliverySemantics`      | `DeliverySemantics` [1]   | Stores the controlled classification or strategy represented by delivery semantics on the event channel. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `ExactlyOnceRequiresIdempotentConsumers` (exactly once requires idempotent consumers) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition.                                                                                               | Exactly one of: `AT_MOST_ONCE`, `AT_LEAST_ONCE`, `EFFECTIVELY_ONCE_WITH_IDEMPOTENCY`, `EXACTLY_ONCE_REQUIRED`. Example: `AT_MOST_ONCE`. |

### Relationships

| Relationship                            | Kind and multiplicity                          | Meaning in the model                                                                                                                          |
| --------------------------------------- | ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `service` → `ServerlessService`         | reference; read-only, [1]; opposite `channels` | References the serverless service element(s) used as service by this event channel; the target may be shared elsewhere in the model.          |
| `eventTypes` → `EventType`              | reference, [*]                                 | References the event type element(s) used as event types by this event channel; the target may be shared elsewhere in the model.              |
| `producers` → `Function`                | reference, [*]                                 | References the function element(s) used as producers by this event channel; the target may be shared elsewhere in the model.                  |
| `consumers` → `Function`                | reference, [*]                                 | References the function element(s) used as consumers by this event channel; the target may be shared elsewhere in the model.                  |
| `workflowConsumers` → `Workflow`        | reference, [*]                                 | References the workflow element(s) used as workflow consumers by this event channel; the target may be shared elsewhere in the model.         |
| `externalProducers` → `ExternalAdapter` | reference, [*]                                 | References the external adapter element(s) used as external producers by this event channel; the target may be shared elsewhere in the model. |
| `externalConsumers` → `ExternalAdapter` | reference, [*]                                 | References the external adapter element(s) used as external consumers by this event channel; the target may be shared elsewhere in the model. |
| `resilience` → `ResiliencePolicy`       | reference, [?]                                 | References the resilience policy element(s) used as resilience by this event channel; the target may be shared elsewhere in the model.        |
| `observability` → `ObservabilityConfig` | reference, [?]                                 | References the observability config element(s) used as observability by this event channel; the target may be shared elsewhere in the model.  |

## `Queue`

Represents queue in the PIM vocabulary. It specializes `EventChannel` with the details needed for this modeling concern.

Direct supertypes: `EventChannel`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                  | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                       |
| -------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `fifoRequired`             | `Boolean` [1]         | Records whether fifo required applies to queue. The field records an explicit architectural or governance decision as an explicit, reviewable input. Semantic validation: `FifoQueueNeedsDeduplicationOrIdempotency` (fifo queue needs deduplication or idempotency) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition. Transformation role: ETL rule `Queue2SqsQueue` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `Queue`.                                                                                                                                                   | Either `true` or `false`. Example: `true`.                                                        |
| `deduplicationRequired`    | `Boolean` [1]         | Records whether deduplication required applies to queue. This keeps the decision explicit even when the element's class or relationships remain unchanged. Semantic validation: `FifoQueueNeedsDeduplicationOrIdempotency` (fifo queue needs deduplication or idempotency) in `mde/validation/pim/rules/integration.evl` the related value or object must be explicitly provided. Transformation role: ETL rule `Queue2SqsQueue` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `Queue`.                                                                                                                                          | Either `true` or `false`. Example: `true`.                                                        |
| `maxReceiveAttempts`       | `Integer` [1]         | For a queue, the model records the max receive attempts value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `RetryQueueNeedsDeadLetterChannel` (retry queue needs dead letter channel) in `mde/validation/pim/rules/integration.evl` the related value or object must be explicitly provided. Transformation role: ETL rule `Queue2SqsQueue` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `Queue`.                                                                                                                                       | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.  |
| `visibilityTimeoutSeconds` | `Integer` [1]         | Records the visibility timeout seconds duration or limit, expressed in seconds for the queue. This keeps the decision explicit even when the element's class or relationships remain unchanged. Semantic validation: `QueueShouldHaveVisibilityTimeout` (queue should have visibility timeout) in `mde/validation/pim/rules/integration.evl` the related value or object must be explicitly provided. Transformation role: ETL rule `Queue2SqsQueue` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `Queue`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `30`. |
| `messageRetentionSeconds`  | `Integer` [1]         | Records human explanation of message retention seconds for queue. It keeps human review and generated guidance explicit during review and transformation, so later steps do not have to infer it. Transformation role: ETL rule `Queue2SqsQueue` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `Queue`.                                                                                                                                                                                                                                                                                                                          | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.  |
| `longPollingRequired`      | `Boolean` [1]         | Records whether long polling required applies to queue. This keeps the decision explicit even when the element's class or relationships remain unchanged. Transformation role: ETL rule `Queue2SqsQueue` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `Queue`.                                                                                                                                                                                                                                                                                                                                                                         | Either `true` or `false`. Example: `true`.                                                        |

### Relationships

| Relationship                  | Kind and multiplicity | Meaning in the model                                                                                                        |
| ----------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `deadLetterChannel` → `Queue` | reference, [?]        | References the queue element(s) used as dead letter channel by this queue; the target may be shared elsewhere in the model. |
| `batchPolicy` → `BatchPolicy` | reference, [?]        | References the batch policy element(s) used as batch policy by this queue; the target may be shared elsewhere in the model. |

## `Topic`

Represents topic in the PIM vocabulary. It specializes `EventChannel` with the details needed for this modeling concern.

Direct supertypes: `EventChannel`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute           | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                               | Accepted values and example                                                                                                 |
| ------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `subscriptionMode`  | `String` [1]          | Records controlled classification or strategy represented by subscription mode for topic. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                     | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Topic Subscription Mode`. |
| `filteringRequired` | `Boolean` [1]         | For a topic, the model records whether filtering required applies. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `FilteringTopicShouldUseSubscriptionFilters` (filtering topic should use subscription filters) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition. | Either `true` or `false`. Example: `true`.                                                                                  |

### Relationships

| Relationship                     | Kind and multiplicity                   | Meaning in the model                                                                                              |
| -------------------------------- | --------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `subscriptions` → `Subscription` | containment, [*]; opposite `ownerTopic` | Contains the subscription element(s) that make up this topic; the contained objects belong to this model element. |

## `EventBus`

Represents event bus in the PIM vocabulary. It specializes `EventChannel` with the details needed for this modeling concern.

Direct supertypes: `EventChannel`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                   | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                | Accepted values and example                                                                                             |
| --------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `routingExpressionLanguage` | `String` [1]          | For a event bus, the model records the routing expression language value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`. |

### Relationships

| Relationship                        | Kind and multiplicity                 | Meaning in the model                                                                                                        |
| ----------------------------------- | ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `routingRules` → `EventRoutingRule` | containment, [*]; opposite `eventBus` | Contains the event routing rule element(s) that make up this event bus; the contained objects belong to this model element. |

## `Schedule`

Represents schedule in the PIM vocabulary. It specializes `IntegrationElement`, `DeployableElement`, `InvocationSource` with the details needed for this modeling concern.

Direct supertypes: `IntegrationElement`, `DeployableElement`, `InvocationSource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Accepted values and example                                                                                             |
| -------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| `scheduleExpression` | `String` [1]          | Stores the expression or rule that governs schedule expression on the schedule. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `ScheduleExpressionRequired` (schedule expression required) in `mde/validation/pim/rules/integration.evl` the value must be present and non-blank. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `Schedule`.                                                                                                                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`. |
| `enabled`            | `Boolean` [1]         | For a schedule, the model records whether enabled applies. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `ScheduleShouldDeclareTimezone` (schedule should declare timezone) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition. `EnabledScheduleHasSingleTarget` (enabled schedule has single target) in `mde/validation/pim/rules/integration.evl` the value must remain absent in this modeling situation. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `Schedule`. | Either `true` or `false`. Example: `true`.                                                                              |
| `timeZone`           | `String` [1]          | For a schedule, the model records the time zone value. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `ScheduleShouldDeclareTimezone` (schedule should declare timezone) in `mde/validation/pim/rules/integration.evl` the value must be present and non-blank. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `Schedule`.                                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Schedule Time Zone`.  |

### Relationships

| Relationship                    | Kind and multiplicity                           | Meaning in the model                                                                                                            |
| ------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `service` → `ServerlessService` | reference; read-only, [1]; opposite `schedules` | References the serverless service element(s) used as service by this schedule; the target may be shared elsewhere in the model. |
| `targets` → `RoutingTarget`     | reference, [*]                                  | References the routing target element(s) used as targets by this schedule; the target may be shared elsewhere in the model.     |

## `Subscription`

Represents subscription in the PIM vocabulary. It specializes `TraceableElement` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Accepted values and example                                                                                             |
| -------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `filterExpression`   | `String` [1]          | Records the expression or rule that governs filter expression for the subscription. This keeps the decision explicit even when the element's class or relationships remain unchanged. Semantic validation: `DeadLetterSubscriptionShouldExplainHandling` (dead letter subscription should explain handling) in `mde/validation/pim/rules/integration.evl` the value must be present and non-blank. Transformation role: ETL rule `Subscription2SnsSubscription` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `Subscription`. ETL rule `Subscription2SnsSubscription` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `Subscription`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`. |
| `rawDelivery`        | `Boolean` [1]         | Records whether raw delivery applies to subscription. This keeps the decision explicit even when the element's class or relationships remain unchanged. Transformation role: ETL rule `Subscription2SnsSubscription` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `Subscription`.                                                                                                                                                                                                                                                                                                                                                                                                                  | Either `true` or `false`. Example: `false`.                                                                             |
| `deadLetterRequired` | `Boolean` [1]         | For a subscription, the model records whether dead letter required applies. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `DeadLetterSubscriptionShouldExplainHandling` (dead letter subscription should explain handling) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition. Transformation role: ETL rule `Subscription2SnsSubscription` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `Subscription`.                                                                                                                                                     | Either `true` or `false`. Example: `true`.                                                                              |

### Relationships

| Relationship                    | Kind and multiplicity                               | Meaning in the model                                                                                                                |
| ------------------------------- | --------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `filter` → `Expression`         | containment, [?]                                    | Contains the expression element(s) that make up this subscription; the contained objects belong to this model element.              |
| `ownerTopic` → `Topic`          | reference; read-only, [1]; opposite `subscriptions` | References the topic element(s) used as owner topic by this subscription; the target may be shared elsewhere in the model.          |
| `channel` → `EventChannel`      | reference, [1]                                      | References the event channel element(s) used as channel by this subscription; the target may be shared elsewhere in the model.      |
| `target` → `SubscriptionTarget` | reference, [1]                                      | References the subscription target element(s) used as target by this subscription; the target may be shared elsewhere in the model. |

## `EventRoutingRule`

Represents event routing rule in the PIM vocabulary. It specializes `TraceableElement`, `InvocationSource`, `PolicyTarget` with the details needed for this modeling concern.

Direct supertypes: `TraceableElement`, `InvocationSource`, `PolicyTarget`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                                                 |
| --------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `eventPattern`        | `String` [1]          | Stores the validation pattern for event pattern on the event routing rule. The field records input/schema validation as an explicit, reviewable input. Semantic validation: `RoutingRuleHasPatternOrSchedule` (routing rule has pattern or schedule) in `mde/validation/pim/rules/integration.evl` the value must be present and non-blank. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventRoutingRule`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `^[A-Z][A-Za-z0-9_-]*$`.                   |
| `scheduleExpression`  | `String` [1]          | Records the expression or rule that governs schedule expression for the event routing rule. This keeps the decision explicit even when the element's class or relationships remain unchanged. Semantic validation: `RoutingRuleHasPatternOrSchedule` (routing rule has pattern or schedule) in `mde/validation/pim/rules/integration.evl` the value must be present and non-blank. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventRoutingRule`.                                                                                                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`.                     |
| `enabled`             | `Boolean` [1]         | Records whether enabled applies to event routing rule. It preserves an explicit architectural or governance decision through review and transformation, so later steps do not have to infer it. Semantic validation: `RoutingRuleHasPatternOrSchedule` (routing rule has pattern or schedule) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition. `EnabledRoutingRuleHasTargets` (enabled routing rule has targets) in `mde/validation/pim/rules/integration.evl` the value must remain absent in this modeling situation. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `EventRoutingRule`.    | Either `true` or `false`. Example: `true`.                                                                                                  |
| `inputTransformation` | `String` [1]          | Records the input transformation value for event routing rule. It keeps this decision explicit during review and transformation, so later steps do not have to infer it. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement.                                                                                                                                                                                                                                                                                                                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Routing Rule Input Transformation`. |

### Relationships

| Relationship                      | Kind and multiplicity                              | Meaning in the model                                                                                                                        |
| --------------------------------- | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `eventBus` → `EventBus`           | reference; read-only, [1]; opposite `routingRules` | References the event bus element(s) used as event bus by this event routing rule; the target may be shared elsewhere in the model.          |
| `eventTypes` → `EventType`        | reference, [*]                                     | References the event type element(s) used as event types by this event routing rule; the target may be shared elsewhere in the model.       |
| `targets` → `RoutingTarget`       | reference, [+]                                     | References the routing target element(s) used as targets by this event routing rule; the target may be shared elsewhere in the model.       |
| `resilience` → `ResiliencePolicy` | reference, [?]                                     | References the resilience policy element(s) used as resilience by this event routing rule; the target may be shared elsewhere in the model. |

## `Flow`

An abstract flow concept. Use one of its concrete subtypes when creating a model instance; the shared attributes and relationships defined here still apply.

Direct supertypes: `TraceableElement`, `PolicyTarget`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                                                  |
| ---------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `flowPurpose`          | `String` [1]          | Stores the human explanation of flow purpose on the flow. The field keeps the model explicit and reviewable during review and transformation instead of leaving the decision to an inferred default. Semantic validation: `FlowPurposeRequired` (flow purpose required) in `mde/validation/pim/rules/integration.evl` the value must be present and non-blank.                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Reserve inventory for a confirmed order.`. |
| `criticalPath`         | `Boolean` [1]         | For a flow, the model records whether critical path applies. Keeping the fact with its owning element lets validation and refinement inspect it before artifact generation. Semantic validation: `CriticalFlowNeedsResilienceAndObservability` (critical flow needs resilience and observability) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition.            | Either `true` or `false`. Example: `false`.                                                                                                  |
| `containsPersonalData` | `Boolean` [1]         | Records the data-sensitivity classification for contains personal data in flow. It keeps privacy, encryption, and access decisions explicit during review and transformation, so later steps do not have to infer them. Semantic validation: `PersonalDataFlowNeedsPolicy` (personal data flow needs policy) in `mde/validation/pim/rules/integration.evl` the feature participates in a semantic validation condition. | Either `true` or `false`. Example: `false`.                                                                                                  |

### Relationships

| Relationship                            | Kind and multiplicity | Meaning in the model                                                                                                                |
| --------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `source` → `FlowEndpoint`               | reference, [1]        | References the flow endpoint element(s) used as source by this flow; the target may be shared elsewhere in the model.               |
| `target` → `FlowEndpoint`               | reference, [1]        | References the flow endpoint element(s) used as target by this flow; the target may be shared elsewhere in the model.               |
| `resilience` → `ResiliencePolicy`       | reference, [?]        | References the resilience policy element(s) used as resilience by this flow; the target may be shared elsewhere in the model.       |
| `observability` → `ObservabilityConfig` | reference, [?]        | References the observability config element(s) used as observability by this flow; the target may be shared elsewhere in the model. |
| `policies` → `ArchitecturePolicy`       | reference, [*]        | References the architecture policy element(s) used as policies by this flow; the target may be shared elsewhere in the model.       |

## `RequestResponseFlow`

Represents request response flow in the PIM vocabulary. It specializes `Flow` with the details needed for this modeling concern.

Direct supertypes: `Flow`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                       | Accepted values and example                 |
| ------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| `synchronous` | `Boolean` [1]         | Records whether synchronous applies to request response flow. This keeps the decision explicit even when the element's class or relationships remain unchanged. The repository contains no direct EVL rule, ETL assignment, or artifact-generator read for this declared field. For now, it remains a model-level fact for review and future refinement. | Either `true` or `false`. Example: `false`. |

### Relationships

| Relationship            | Kind and multiplicity | Meaning in the model                                                                                                                  |
| ----------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `apiRoute` → `ApiRoute` | reference, [1]        | References the api route element(s) used as api route by this request response flow; the target may be shared elsewhere in the model. |

## `EventFlow`

Represents event flow in the PIM vocabulary. It specializes `Flow` with the details needed for this modeling concern.

Direct supertypes: `Flow`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                                                                                         |
| -------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `eventType` → `EventType`  | reference, [1]        | References the event type element(s) used as event type by this event flow; the target may be shared elsewhere in the model. |
| `channel` → `EventChannel` | reference, [1]        | References the event channel element(s) used as channel by this event flow; the target may be shared elsewhere in the model. |

## `MessageFlow`

Represents message flow in the PIM vocabulary. It specializes `Flow` with the details needed for this modeling concern.

Direct supertypes: `Flow`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                                                                                           |
| -------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `messageSchema` → `Schema` | reference, [1]        | References the schema element(s) used as message schema by this message flow; the target may be shared elsewhere in the model. |
| `queue` → `Queue`          | reference, [1]        | References the queue element(s) used as queue by this message flow; the target may be shared elsewhere in the model.           |

## `PubSubFlow`

Represents pub sub flow in the PIM vocabulary. It specializes `Flow` with the details needed for this modeling concern.

Direct supertypes: `Flow`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                     | Kind and multiplicity | Meaning in the model                                                                                                                |
| -------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `topic` → `Topic`                | reference, [1]        | References the topic element(s) used as topic by this pub sub flow; the target may be shared elsewhere in the model.                |
| `subscriptions` → `Subscription` | reference, [*]        | References the subscription element(s) used as subscriptions by this pub sub flow; the target may be shared elsewhere in the model. |

## `OrchestrationFlow`

Represents orchestration flow in the PIM vocabulary. It specializes `Flow` with the details needed for this modeling concern.

Direct supertypes: `Flow`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship            | Kind and multiplicity | Meaning in the model                                                                                                             |
| ----------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `workflow` → `Workflow` | reference, [1]        | References the workflow element(s) used as workflow by this orchestration flow; the target may be shared elsewhere in the model. |

## `ExternalIntegrationFlow`

Represents external integration flow in the PIM vocabulary. It specializes `Flow` with the details needed for this modeling concern.

Direct supertypes: `Flow`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                  | Kind and multiplicity | Meaning in the model                                                                                                                           |
| ----------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `adapter` → `ExternalAdapter` | reference, [1]        | References the external adapter element(s) used as adapter by this external integration flow; the target may be shared elsewhere in the model. |
