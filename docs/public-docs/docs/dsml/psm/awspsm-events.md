# Amazon EventBridge resources

EventBridge classes cover buses, rules, targets, schedules, pipes, API destinations, connections, input mapping, and retry policy.

Source: `mde/metamodels/psm/awspsm-events.emf`.

## `EventBridgeBus`

An AWS EventBridge event bus resource. It provides the event-routing boundary on which rules, targets, policies, and cross-service event delivery are organized.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute         | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                            | Accepted values and example                                                                                               |
| ----------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `busName`         | `String` [1]          | The bus name that identifies this event bridge bus in the model and its generated AWS configuration.                                                                                                                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders-domain`.         |
| `eventSourceName` | `String` [1]          | The partner or AWS event source associated with this bus. Transformation role: ETL rule `EventBus2EventBridgeBus` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeBus`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeBusExample`. |

### Relationships

| Relationship                        | Kind and multiplicity                     | Meaning in the model                                                                                                                                                                                                                                                           |
| ----------------------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `rules` → `EventBridgeRule`         | reference, [*]; opposite `bus`            | The `rules` reference on `EventBridgeBus` owns the ordered rule records that determine the behavior. An `EventBridgeRule` can remain independently owned and can participate in other parts of the model. Its opposite `bus` exposes the same connection from the target side. |
| `archives` → `EventBridgeArchive`   | reference, [*]; opposite `eventSourceBus` | Event archives that capture events from this bus.                                                                                                                                                                                                                              |
| `policies` → `EventBridgeBusPolicy` | reference, [*]; opposite `bus`            | The `policies` reference on `EventBridgeBus` places rules inside the proposed bounded context. An `EventBridgeBusPolicy` can remain independently owned and can participate in other parts of the model. Its opposite `bus` exposes the same connection from the target side.  |

## `EventBridgeBusPolicy`

`EventBridgeBusPolicy` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge bus policy. Its declaration gives the concept a precise home through policy document, bus. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                  | Accepted values and example                                                                                          |
| ------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `statementId` | `String` [1]          | The identifier of this permission statement within the event-bus policy.                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.        |
| `policyJson`  | `String` [1]          | The serialized representation of policy on this event bridge bus policy. It carries provider-specific structure that is intentionally retained as document content. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |

### Relationships

| Relationship                           | Kind and multiplicity               | Meaning in the model                                                                                                                                                                                                               |
| -------------------------------------- | ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `policyDocument` → `IamPolicyDocument` | containment, [?]                    | The `policyDocument` containment on `EventBridgeBusPolicy` owns the structured IAM policy document used by the resource. The `IamPolicyDocument` objects are owned by `EventBridgeBusPolicy` and remain part of its model subtree. |
| `bus` → `EventBridgeBus`               | reference, [1]; opposite `policies` | The EventBridge bus on which this rule, archive, or policy operates.                                                                                                                                                               |

## `EventBridgeRule`

An AWS EventBridge rule resource that selects events or time-based invocations. Its pattern, schedule, targets, retry, archive, and enablement fields determine the routing behavior.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Accepted values and example                                                                                             |
| -------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `ruleName`           | `String` [1]          | The rule name that identifies this event bridge rule in the model and its generated AWS configuration.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `OrderConfirmedRule`.  |
| `descriptionText`    | `String` [1]          | The provider-facing descriptive text emitted for this resource. It gives an operator context in the generated template and console without carrying runtime behavior. Within `EventBridgeRule`, it applies to this specific element and its role in the surrounding model.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`.   |
| `eventPatternJson`   | `String` [1]          | Records validation pattern for event pattern json for event bridge rule. It keeps input/schema validation explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `RuleHasPatternOrSchedule` (rule has pattern or schedule) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. `RuleDoesNotMixPatternAndSchedule` (rule does not mix pattern and schedule) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Transformation role: ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeRule`. ETL rule `EventRoutingRule2EventBridgeRule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` reads or derives this feature while refining `EventBridgeRule`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.    |
| `scheduleExpression` | `String` [1]          | The expression used to determine schedule for this event bridge rule. It keeps executable or provider-interpreted logic visible to validation and generation.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`. |
| `state`              | `String` [1]          | The requested operational state of the rule, deciding whether event delivery is active.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.              |

### Relationships

| Relationship                    | Kind and multiplicity            | Meaning in the model                                                                                                                                                                                            |
| ------------------------------- | -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `eventPattern` → `EventPattern` | containment, [?]                 | The `eventPattern` containment on `EventBridgeRule` selects the events that activate the routing rule. The `EventPattern` objects are owned by `EventBridgeRule` and remain part of its model subtree.          |
| `targets` → `EventBridgeTarget` | containment, [+]                 | The `targets` containment on `EventBridgeRule` lists the destinations that receive this event or schedule. The `EventBridgeTarget` objects are owned by `EventBridgeRule` and remain part of its model subtree. |
| `bus` → `EventBridgeBus`        | reference, [?]; opposite `rules` | The EventBridge bus on which this rule, archive, or policy operates.                                                                                                                                            |

## `EventPattern`

The structured subset of an EventBridge event used for rule matching. Each populated field narrows the events accepted by the containing rule.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                               |
| ------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `sources`     | `String` [*]          | The EventBridge `source` values accepted by this pattern.                                                                                                                                                                                                                                                                              | A collection of values. Example: [`Event Pattern Sources`, `Event Pattern Sources-2`].                                    |
| `detailTypes` | `String` [*]          | The event `detail-type` values accepted by this pattern.                                                                                                                                                                                                                                                                               | A collection of values. Example: [`BUSINESS`, `BUSINESS-2`].                                                              |
| `account`     | `String` [1]          | The AWS account identifier used as an EventBridge pattern criterion. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Pattern Account`. |
| `region`      | `String` [1]          | The AWS region value used as a pattern criterion.                                                                                                                                                                                                                                                                                      | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `us-east-1`.             |
| `detailJson`  | `String` [1]          | Records serialized JSON representation of detail for event pattern. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.      |

### Relationships

This class declares no direct relationships.

## `EventBridgeTarget`

`EventBridgeTarget` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge target. Its declaration gives the concept a precise home through input, parameters, retry policy. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity       | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Accepted values and example                                                                                                                               |
| --------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `targetId`      | `String` [1]                | The identifier of this target within its EventBridge rule. Semantic validation: `TargetHasResourceOrArn` (target has resource or arn) in `mde/validation/psm/rules/events.evl` the rule's diagnostic or remediation guidance refers to this feature. `CriticalEventTargetsHaveRetryOrDlq` (critical event targets have retry or dlq) in `mde/validation/psm/rules/events.evl` the rule's diagnostic or remediation guidance refers to this feature. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                                             |
| `arnExpression` | `String` [1]                | The expression used to determine arn for this event bridge target. It keeps executable or provider-interpreted logic visible to validation and generation.                                                                                                                                                                                                                                                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `inputPath`     | `String` [1]                | A JSONPath expression selecting the event fragment passed to the target.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`.                                     |
| `roleArn`       | `String` [1]                | The IAM role ARN EventBridge assumes when invoking this target. Semantic validation: `NonLambdaTargetHasInvokeRole` (non lambda target has invoke role) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank.                                                                                                                                                                                                                                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `targetKind`    | `EventBridgeTargetKind` [1] | The controlled value used for target kind on this event bridge target. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                                                                                                                                                                                                                            | Exactly one of: `LAMBDA`, `SQS`, `SNS`, `STEP_FUNCTIONS`, `API_DESTINATION`, `EVENT_BUS`, `LOG_GROUP`, `OTHER_AWS_RESOURCE`. Example: `LAMBDA`.           |

### Relationships

| Relationship                                       | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                |
| -------------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `input` → `ValueExpression`                        | containment, [?]      | The `input` containment on `EventBridgeTarget` lists the information needed to perform the operation. The `ValueExpression` objects are owned by `EventBridgeTarget` and remain part of its model subtree.                          |
| `parameters` → `EventBridgeTargetParameters`       | containment, [?]      | The `parameters` containment on `EventBridgeTarget` keeps the named configuration inputs in the configuration set. The `EventBridgeTargetParameters` objects are owned by `EventBridgeTarget` and remain part of its model subtree. |
| `retryPolicy` → `AwsRetryPolicy`                   | containment, [?]      | The `retryPolicy` containment on `EventBridgeTarget` attaches the access or governance decision represented by retry policy. The `AwsRetryPolicy` objects are owned by `EventBridgeTarget` and remain part of its model subtree.    |
| `inputTransformer` → `EventBridgeInputTransformer` | containment, [?]      | The mapping that reshapes a matched event before target delivery.                                                                                                                                                                   |
| `targetResource` → `AwsResource`                   | reference, [?]        | The `targetResource` reference on `EventBridgeTarget` identifies the concrete resource that receives this integration or policy. An `AwsResource` can remain independently owned and can participate in other parts of the model.   |
| `role` → `IamRole`                                 | reference, [?]        | The `role` reference on `EventBridgeTarget` attaches the execution role that grants the resource its AWS permissions. An `IamRole` can remain independently owned and can participate in other parts of the model.                  |
| `deadLetterQueue` → `SqsQueue`                     | reference, [?]        | The SQS queue that receives messages or events after normal delivery or processing can no longer continue.                                                                                                                          |

## `EventBridgeTargetParameters`

`EventBridgeTargetParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge target parameters. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `EventBridgeSqsTargetParameters`

`EventBridgeSqsTargetParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge sqs target parameters. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `EventBridgeTargetParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                     | Accepted values and example                                                                                   |
| ---------------- | --------------------- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `messageGroupId` | `String` [1]          | The message-group identifier supplied when delivering to a FIFO queue. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`. |

### Relationships

This class declares no direct relationships.

## `EventBridgeHttpTargetParameters`

`EventBridgeHttpTargetParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge http target parameters. Its declaration gives the concept a precise home through header parameters, query string parameters, path parameter values. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `EventBridgeTargetParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                              | Kind and multiplicity | Meaning in the model                                                            |
| ----------------------------------------- | --------------------- | ------------------------------------------------------------------------------- |
| `headerParameters` → `HttpParameter`      | containment, [*]      | HTTP header values added to an outbound EventBridge request.                    |
| `queryStringParameters` → `HttpParameter` | containment, [*]      | Query-string values added to an outbound EventBridge request.                   |
| `pathParameterValues` → `HttpParameter`   | containment, [*]      | Values substituted into path parameters of an outbound API-destination request. |

## `HttpParameter`

`HttpParameter` is a supporting value object in the AWS platform-specific model. It carries http parameter as explicit model data. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                      | Accepted values and example                                                                                 |
| --------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `key`     | `String` [1]          | The header, query-string, or body parameter name, according to the collection that owns it.                                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |
| `value`   | `String` [1]          | The value carried by this parameter or expression. It is kept separate from the name so references can remain stable while deployment values change. Within `HttpParameter`, it applies to this specific element and its role in the surrounding model. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `READY`.   |

### Relationships

This class declares no direct relationships.

## `EventBridgeBatchTargetParameters`

`EventBridgeBatchTargetParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge batch target parameters. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `EventBridgeTargetParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                           | Accepted values and example                                                                                                                                            |
| --------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `jobDefinition` | `String` [1]          | The AWS Batch job-definition name or ARN used for target invocations.                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `A confirmed order is one accepted for fulfillment by the business.`. |
| `jobName`       | `String` [1]          | The job name that identifies this event bridge batch target parameters in the model and its generated AWS configuration.                                                                     | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeBatchTargetParametersExample`.                            |
| `arraySize`     | `Integer` [1]         | The numeric value used for array size on this event bridge batch target parameters. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.   | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                                                       |
| `jobAttempts`   | `Integer` [1]         | The numeric value used for job attempts on this event bridge batch target parameters. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                                                       |

### Relationships

This class declares no direct relationships.

## `EventBridgeInputTransformer`

`EventBridgeInputTransformer` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge input transformer. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute           | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                       | Accepted values and example                                                                                                                       |
| ------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `inputPathsMapJson` | `String` [1]          | The serialized representation of input paths map on this event bridge input transformer. It carries provider-specific structure that is intentionally retained as document content. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                              |
| `inputTemplate`     | `String` [1]          | The template that builds target input from variables in `inputPathsMap`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Bridge Input Transformer Input Template`. |

### Relationships

This class declares no direct relationships.

## `EventBridgeArchive`

`EventBridgeArchive` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge archive. Its declaration gives the concept a precise home through event source bus. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                             | Accepted values and example                                                                                                   |
| ------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| `archiveName`      | `String` [1]          | The archive name that identifies this event bridge archive in the model and its generated AWS configuration.                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeArchiveExample`. |
| `eventPatternJson` | `String` [1]          | The serialized representation of event pattern on this event bridge archive. It carries provider-specific structure that is intentionally retained as document content.        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.          |
| `retentionDays`    | `Integer` [1]         | The numeric value used for retention days on this event bridge archive. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                              |

### Relationships

| Relationship                        | Kind and multiplicity               | Meaning in the model                                                                                                                                                                                                                                                                                                     |
| ----------------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `eventSourceBus` → `EventBridgeBus` | reference, [1]; opposite `archives` | The `eventSourceBus` reference on `EventBridgeArchive` connects this element to the event or message path represented by event source bus. An `EventBridgeBus` can remain independently owned and can participate in other parts of the model. Its opposite `archives` exposes the same connection from the target side. |

## `EventBridgeSchedule`

`EventBridgeSchedule` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge schedule. Its declaration gives the concept a precise home through flexible time window, target, role. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                    | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                                    |
| ---------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `scheduleName`               | `String` [1]          | The schedule name that identifies this event bridge schedule in the model and its generated AWS configuration. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeScheduleExample`. |
| `scheduleExpression`         | `String` [1]          | The `rate`, `cron`, or one-time expression that determines when the schedule fires. Semantic validation: `ScheduleHasExpressionAndRole` (schedule has expression and role) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`.                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`.        |
| `scheduleExpressionTimezone` | `String` [1]          | The time zone in which the schedule expression is evaluated.                                                                                                                                                                                                                                                                                                                                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`.        |
| `flexibleTimeWindowJson`     | `String` [1]          | The serialized representation of flexible time window on this event bridge schedule. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.           |
| `state`                      | `String` [1]          | The requested enabled or disabled state of the schedule. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`.                                                                                                                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.                     |
| `descriptionText`            | `String` [1]          | The provider-facing descriptive text emitted for this resource. It gives an operator context in the generated template and console without carrying runtime behavior. Within `EventBridgeSchedule`, it applies to this specific element and its role in the surrounding model. Transformation role: ETL rule `Schedule2EventBridgeSchedule` in `mde/transformations/pim-to-awspsm/data-messaging-events.etl` assigns or materializes this feature while refining `EventBridgeSchedule`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`.          |

### Relationships

| Relationship                                           | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                 |
| ------------------------------------------------------ | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `flexibleTimeWindow` → `EventBridgeFlexibleTimeWindow` | containment, [?]      | The policy allowing a scheduled invocation to occur within a bounded window.                                                                                                                                         |
| `target` → `EventBridgeTarget`                         | containment, [1]      | The `target` containment on `EventBridgeSchedule` marks the destination of a directed relationship. The `EventBridgeTarget` objects are owned by `EventBridgeSchedule` and remain part of its model subtree.         |
| `role` → `IamRole`                                     | reference, [1]        | The `role` reference on `EventBridgeSchedule` attaches the execution role that grants the resource its AWS permissions. An `IamRole` can remain independently owned and can participate in other parts of the model. |

## `EventBridgeFlexibleTimeWindow`

`EventBridgeFlexibleTimeWindow` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge flexible time window. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                | Type and multiplicity | What it captures and why it exists                                                                                                                                                                     | Accepted values and example                                                                                                                |
| ------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `mode`                   | `String` [1]          | Whether invocation must occur at the exact time or may fall within a flexible window.                                                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Bridge Flexible Time Window Mode`. |
| `maximumWindowInMinutes` | `Integer` [1]         | The numeric value used for maximum window in minutes on this event bridge flexible time window. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                           |

### Relationships

This class declares no direct relationships.

## `EventBridgePipe`

`EventBridgePipe` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge pipe. Its declaration gives the concept a precise home through source resource, target resource, enrichment function. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                                                               |
| ---------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `pipeName`             | `String` [1]          | The pipe name that identifies this event bridge pipe in the model and its generated AWS configuration.                                                                                                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgePipeExample`.                                |
| `sourceArn`            | `String` [1]          | The ARN of the stream, queue, or other supported source read by the pipe.                                                                                                                                                                                                                                                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `targetArn`            | `String` [1]          | The ARN of the resource to which the pipe delivers processed events. Semantic validation: `PipeHasSourceTargetAndRole` (pipe has source target and role) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `filterCriteriaJson`   | `String` [1]          | The serialized representation of filter criteria on this event bridge pipe. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |
| `enrichmentArn`        | `String` [1]          | The ARN of the optional enrichment invoked before target delivery.                                                                                                                                                                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `sourceParametersJson` | `String` [1]          | The serialized representation of source parameters on this event bridge pipe. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |
| `targetParametersJson` | `String` [1]          | The serialized representation of target parameters on this event bridge pipe. It carries provider-specific structure that is intentionally retained as document content. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |
| `desiredState`         | `String` [1]          | The requested running state of the pipe after deployment. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ACTIVE`.                                                |

### Relationships

| Relationship                               | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                            |
| ------------------------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sourceResource` → `AwsResource`           | reference, [?]        | The `sourceResource` reference on `EventBridgePipe` identifies the source represented by source resource. An `AwsResource` can remain independently owned and can participate in other parts of the model.                      |
| `targetResource` → `AwsResource`           | reference, [?]        | The `targetResource` reference on `EventBridgePipe` identifies the concrete resource that receives this integration or policy. An `AwsResource` can remain independently owned and can participate in other parts of the model. |
| `enrichmentFunction` → `AwsLambdaFunction` | reference, [?]        | The Lambda function used as the pipe's optional enrichment step.                                                                                                                                                                |
| `role` → `IamRole`                         | reference, [1]        | The `role` reference on `EventBridgePipe` attaches the execution role that grants the resource its AWS permissions. An `IamRole` can remain independently owned and can participate in other parts of the model.                |

## `EventBridgeAuthParameters`

`EventBridgeAuthParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge auth parameters. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `EventBridgeApiKeyAuthParameters`

`EventBridgeApiKeyAuthParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge api key auth parameters. Its declaration gives the concept a precise home through api key value. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `EventBridgeAuthParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute    | Type and multiplicity | What it captures and why it exists                                                                                           | Accepted values and example                                                                                 |
| ------------ | --------------------- | ---------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `apiKeyName` | `String` [1]          | The api key name that identifies this event bridge api key auth parameters in the model and its generated AWS configuration. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                |
| --------------------------------- | --------------------- | ------------------------------------------------------------------- |
| `apiKeyValue` → `ValueExpression` | containment, [1]      | The protected value expression containing the connection's API key. |

## `EventBridgeBasicAuthParameters`

`EventBridgeBasicAuthParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge basic auth parameters. Its declaration gives the concept a precise home through username, password. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `EventBridgeAuthParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                   | Kind and multiplicity | Meaning in the model                                                        |
| ------------------------------ | --------------------- | --------------------------------------------------------------------------- |
| `username` → `ValueExpression` | containment, [1]      | The protected value expression supplying the basic-authentication username. |
| `password` → `ValueExpression` | containment, [1]      | The protected value expression supplying the basic-authentication password. |

## `EventBridgeOAuthParameters`

`EventBridgeOAuthParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge o auth parameters. Its declaration gives the concept a precise home through client id, client secret, oauth http parameters. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `EventBridgeAuthParameters`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute               | Type and multiplicity       | What it captures and why it exists                                                                                                                                                           | Accepted values and example                                                                                                                              |
| ----------------------- | --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `authorizationEndpoint` | `String` [1]                | The OAuth server endpoint used to obtain a client-credentials token. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/values.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Bridge Oauth Parameters Authorization Endpoint`. |
| `httpMethod`            | `EventBridgeHttpMethod` [1] | The controlled value used for http method on this event bridge o auth parameters. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.     | Exactly one of: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`. Example: `GET`.                                                              |

### Relationships

| Relationship                                        | Kind and multiplicity | Meaning in the model                                                   |
| --------------------------------------------------- | --------------------- | ---------------------------------------------------------------------- |
| `clientId` → `ValueExpression`                      | containment, [1]      | The protected value expression containing the OAuth client identifier. |
| `clientSecret` → `ValueExpression`                  | containment, [1]      | The protected value expression containing the OAuth client secret.     |
| `oauthHttpParameters` → `EventBridgeHttpParameters` | containment, [?]      | Additional HTTP values sent while requesting the OAuth token.          |

## `EventBridgeHttpParameters`

`EventBridgeHttpParameters` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge http parameters. Its declaration gives the concept a precise home through header parameters, query string parameters, body parameters. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                              | Kind and multiplicity | Meaning in the model                                          |
| ----------------------------------------- | --------------------- | ------------------------------------------------------------- |
| `headerParameters` → `HttpParameter`      | containment, [*]      | HTTP header values added to an outbound EventBridge request.  |
| `queryStringParameters` → `HttpParameter` | containment, [*]      | Query-string values added to an outbound EventBridge request. |
| `bodyParameters` → `HttpParameter`        | containment, [*]      | Body fields added to an outbound EventBridge request.         |

## `EventBridgeConnection`

`EventBridgeConnection` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge connection. Its declaration gives the concept a precise home through auth parameters, invocation http parameters. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute           | Type and multiplicity                        | What it captures and why it exists                                                                                                                                                                                                                                                                                                        | Accepted values and example                                                                                                      |
| ------------------- | -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `connectionName`    | `String` [1]                                 | The connection name that identifies this event bridge connection in the model and its generated AWS configuration.                                                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeConnectionExample`. |
| `authorizationType` | `EventBridgeConnectionAuthorizationType` [1] | The authentication scheme stored by the connection. It determines which contained parameters are valid. Semantic validation: `ConnectionAuthParametersMatchAuthorizationType` (connection auth parameters match authorization type) in `mde/validation/psm/rules/events.evl` the feature participates in a semantic validation condition. | Exactly one of: `API_KEY`, `BASIC`, `OAUTH_CLIENT_CREDENTIALS`. Example: `API_KEY`.                                              |

### Relationships

| Relationship                                             | Kind and multiplicity | Meaning in the model                                                        |
| -------------------------------------------------------- | --------------------- | --------------------------------------------------------------------------- |
| `authParameters` → `EventBridgeAuthParameters`           | containment, [1]      | Credentials and scheme-specific values owned by the EventBridge connection. |
| `invocationHttpParameters` → `EventBridgeHttpParameters` | containment, [?]      | Headers, query values, and body fields added when the connection is used.   |

## `EventBridgeApiDestination`

`EventBridgeApiDestination` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge api destination. Its declaration gives the concept a precise home through connection. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                      | Type and multiplicity       | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                                                          |
| ------------------------------ | --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `destinationName`              | `String` [1]                | The destination name that identifies this event bridge api destination in the model and its generated AWS configuration. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `EventBridgeApiDestinationExample`.                 |
| `invocationEndpoint`           | `String` [1]                | The HTTPS endpoint invoked by this API destination. Semantic validation: `ApiDestinationHasEndpointAndConnection` (api destination has endpoint and connection) in `mde/validation/psm/rules/events.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Event Bridge Api Destination Invocation Endpoint`. |
| `httpMethod`                   | `EventBridgeHttpMethod` [1] | The controlled value used for http method on this event bridge api destination. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                 | Exactly one of: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`. Example: `GET`.                                                          |
| `invocationRateLimitPerSecond` | `Integer` [1]               | The numeric value used for invocation rate limit per second on this event bridge api destination. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                                               | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                                     |

### Relationships

| Relationship                           | Kind and multiplicity | Meaning in the model                                                          |
| -------------------------------------- | --------------------- | ----------------------------------------------------------------------------- |
| `connection` → `EventBridgeConnection` | reference, [1]        | The EventBridge connection supplying authentication for this API destination. |

## `AwsRetryPolicy`

`AwsRetryPolicy` is a policy record in the AWS platform-specific model. It gives aws retry policy a named place in the design. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                  | Type and multiplicity | What it captures and why it exists                                                                                                                                                       | Accepted values and example                                                                      |
| -------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `maximumRetryAttempts`     | `Integer` [1]         | The numeric value used for maximum retry attempts on this aws retry policy. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.       | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |
| `maximumEventAgeInSeconds` | `Integer` [1]         | The numeric value used for maximum event age in seconds on this aws retry policy. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |

### Relationships

This class declares no direct relationships.
