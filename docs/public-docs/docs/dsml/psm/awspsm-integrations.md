# Cross-resource integration views

Integration views are explicit wiring records used to inspect and validate how AWS resources connect across service boundaries.

Source: `mde/metamodels/psm/awspsm-integrations.emf`.

## `AwsRelationshipView`

`AwsRelationshipView` is an integration view in the AWS platform-specific model. It makes aws relationship view visible as a connection. Its declaration gives the concept a precise home through source, target. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                                            | Accepted values and example                 |
| ----------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| `generated` | `Boolean` [1]         | The boolean decision for generated on this aws relationship view. It keeps an important design choice explicit for review and transformation. | Either `true` or `false`. Example: `false`. |

### Relationships

| Relationship             | Kind and multiplicity | Meaning in the model                                                                                                                                                                               |
| ------------------------ | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `source` → `AwsResource` | reference, [1]        | The `source` reference on `AwsRelationshipView` marks the origin of a directed relationship. An `AwsResource` can remain independently owned and can participate in other parts of the model.      |
| `target` → `AwsResource` | reference, [1]        | The `target` reference on `AwsRelationshipView` marks the destination of a directed relationship. An `AwsResource` can remain independently owned and can participate in other parts of the model. |

## `ApiGatewayLambdaIntegrationView`

`ApiGatewayLambdaIntegrationView` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway lambda integration view. Its declaration gives the concept a precise home through route, function, integration. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                            | Kind and multiplicity | Meaning in the model                                                                              |
| --------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------- |
| `route` → `ApiGatewayRoute`             | reference, [1]        | The API Gateway route through which requests enter this integration path.                         |
| `function` → `AwsLambdaFunction`        | reference, [1]        | The Lambda function that handles requests matched by the route.                                   |
| `integration` → `ApiGatewayIntegration` | reference, [1]        | The API Gateway integration resource carrying the route-to-function invocation configuration.     |
| `permission` → `LambdaPermission`       | reference, [?]        | The optional Lambda resource policy statement that authorizes API Gateway to invoke the function. |

## `EventBridgeLambdaTargetView`

`EventBridgeLambdaTargetView` is an EventBridge deployment record in the AWS platform-specific model. It carries the routing or invocation settings for event bridge lambda target view. Its declaration gives the concept a precise home through rule, target row, function. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                    |
| --------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rule` → `EventBridgeRule`        | reference, [1]        | The EventBridge rule that selects the events represented by this view.                                                                                                                                                  |
| `targetRow` → `EventBridgeTarget` | reference, [1]        | The `targetRow` reference on `EventBridgeLambdaTargetView` identifies the destination represented by target row. An `EventBridgeTarget` can remain independently owned and can participate in other parts of the model. |
| `function` → `AwsLambdaFunction`  | reference, [1]        | The Lambda function invoked for each matching event.                                                                                                                                                                    |
| `permission` → `LambdaPermission` | reference, [?]        | The optional Lambda permission granting EventBridge invocation access.                                                                                                                                                  |

## `SnsLambdaSubscriptionView`

`SnsLambdaSubscriptionView` is an AWS messaging record in the AWS platform-specific model. It carries the delivery setting for sns lambda subscription view. Its declaration gives the concept a precise home through topic, function, subscription. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                       | Kind and multiplicity | Meaning in the model                                                                                      |
| ---------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------- |
| `topic` → `SnsTopic`               | reference, [1]        | The SNS topic on the publishing side of the subscription.                                                 |
| `function` → `AwsLambdaFunction`   | reference, [1]        | The Lambda function that consumes notifications from the topic.                                           |
| `subscription` → `SnsSubscription` | reference, [1]        | The SNS subscription whose protocol, endpoint, filtering, and delivery settings implement the connection. |
| `permission` → `LambdaPermission`  | reference, [?]        | The optional Lambda permission allowing SNS to invoke the subscriber function.                            |

## `SqsLambdaEventSourceView`

`SqsLambdaEventSourceView` is an AWS messaging record in the AWS platform-specific model. It carries the delivery setting for sqs lambda event source view. Its declaration gives the concept a precise home through queue, function, mapping. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                              | Kind and multiplicity | Meaning in the model                                                                                                 |
| ----------------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `queue` → `SqsQueue`                      | reference, [1]        | The SQS queue from which Lambda polls messages.                                                                      |
| `function` → `AwsLambdaFunction`          | reference, [1]        | The Lambda function that processes batches taken from the queue.                                                     |
| `mapping` → `SqsLambdaEventSourceMapping` | reference, [1]        | The event-source mapping that defines polling, batching, retry, filtering, and failure behavior for this connection. |

## `StepFunctionEventBridgeTargetView`

`StepFunctionEventBridgeTargetView` is a Step Functions deployment record in the AWS platform-specific model. It carries the orchestration setting for step function event bridge target view. Its declaration gives the concept a precise home through rule, state machine, target row. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                                | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                          |
| ------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rule` → `EventBridgeRule`                  | reference, [1]        | The EventBridge rule that initiates the workflow path.                                                                                                                                                                        |
| `stateMachine` → `StepFunctionStateMachine` | reference, [1]        | The Step Functions state machine started by the EventBridge target.                                                                                                                                                           |
| `targetRow` → `EventBridgeTarget`           | reference, [1]        | The `targetRow` reference on `StepFunctionEventBridgeTargetView` identifies the destination represented by target row. An `EventBridgeTarget` can remain independently owned and can participate in other parts of the model. |

## `S3LambdaNotificationView`

`S3LambdaNotificationView` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 lambda notification view. Its declaration gives the concept a precise home through bucket, function, rule. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                            |
| --------------------------------- | --------------------- | ------------------------------------------------------------------------------- |
| `bucket` → `S3Bucket`             | reference, [1]        | The S3 bucket that produces object notifications.                               |
| `function` → `AwsLambdaFunction`  | reference, [1]        | The Lambda function receiving the selected bucket events.                       |
| `rule` → `S3NotificationRule`     | reference, [1]        | The notification rule that selects event types and optional object-key filters. |
| `permission` → `LambdaPermission` | reference, [1]        | The Lambda permission authorizing invocation from the bucket.                   |

## `S3QueueNotificationView`

`S3QueueNotificationView` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 queue notification view. Its declaration gives the concept a precise home through bucket, queue, rule. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                     | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                |
| -------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bucket` → `S3Bucket`            | reference, [1]        | The S3 bucket that originates notifications.                                                                                                                                                                                        |
| `queue` → `SqsQueue`             | reference, [1]        | The SQS queue receiving notifications selected by the rule.                                                                                                                                                                         |
| `rule` → `S3NotificationRule`    | reference, [1]        | The bucket notification rule that defines which events are sent to SQS.                                                                                                                                                             |
| `queuePolicy` → `SqsQueuePolicy` | reference, [?]        | The `queuePolicy` reference on `S3QueueNotificationView` attaches the access or governance decision represented by queue policy. A `SqsQueuePolicy` can remain independently owned and can participate in other parts of the model. |

## `S3TopicNotificationView`

`S3TopicNotificationView` is an S3 deployment record in the AWS platform-specific model. It carries the object-storage setting for s3 topic notification view. Its declaration gives the concept a precise home through bucket, topic, rule. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                     | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                |
| -------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bucket` → `S3Bucket`            | reference, [1]        | The S3 bucket that originates notifications.                                                                                                                                                                                        |
| `topic` → `SnsTopic`             | reference, [1]        | The SNS topic receiving notifications selected by the rule.                                                                                                                                                                         |
| `rule` → `S3NotificationRule`    | reference, [1]        | The bucket notification rule that defines which events are published to SNS.                                                                                                                                                        |
| `topicPolicy` → `SnsTopicPolicy` | reference, [?]        | The `topicPolicy` reference on `S3TopicNotificationView` attaches the access or governance decision represented by topic policy. A `SnsTopicPolicy` can remain independently owned and can participate in other parts of the model. |
