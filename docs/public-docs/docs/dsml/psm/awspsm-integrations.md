# Cross-resource integration views

Integration views are explicit wiring records used to inspect and validate how AWS resources connect across service boundaries.

Source: `mde/metamodels/psm/awspsm-integrations.emf`.

## `AwsRelationshipView`

An abstract aws relationship view concept. Use one of its concrete subtypes when creating a model instance; the shared attributes and relationships defined here still apply.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Accepted values and example                 |
| ----------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| `generated` | `Boolean` [1]         | This field holds the yes/no decision about whether generated applies for the aws relationship view. That separation matters because an explicit architectural or governance decision can change the meaning or safety of the element without changing its class or relationships. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Either `true` or `false`. Example: `false`. |

### Relationships

| Relationship             | Kind and multiplicity | Meaning in the model                                                                                                                  |
| ------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `source` → `AwsResource` | reference, [1]        | References the aws resource element(s) used as source by this aws relationship view; the target may be shared elsewhere in the model. |
| `target` → `AwsResource` | reference, [1]        | References the aws resource element(s) used as target by this aws relationship view; the target may be shared elsewhere in the model. |

## `ApiGatewayLambdaIntegrationView`

Represents api gateway lambda integration view in the PSM vocabulary. It specializes `AwsRelationshipView` with the details needed for this modeling concern.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                            | Kind and multiplicity | Meaning in the model                                                                                                                                                |
| --------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `route` → `ApiGatewayRoute`             | reference, [1]        | References the api gateway route element(s) used as route by this api gateway lambda integration view; the target may be shared elsewhere in the model.             |
| `function` → `AwsLambdaFunction`        | reference, [1]        | References the aws lambda function element(s) used as function by this api gateway lambda integration view; the target may be shared elsewhere in the model.        |
| `integration` → `ApiGatewayIntegration` | reference, [1]        | References the api gateway integration element(s) used as integration by this api gateway lambda integration view; the target may be shared elsewhere in the model. |
| `permission` → `LambdaPermission`       | reference, [?]        | References the lambda permission element(s) used as permission by this api gateway lambda integration view; the target may be shared elsewhere in the model.        |

## `EventBridgeLambdaTargetView`

Represents event bridge lambda target view in the PSM vocabulary. It specializes `AwsRelationshipView` with the details needed for this modeling concern.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                       |
| --------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rule` → `EventBridgeRule`        | reference, [1]        | References the event bridge rule element(s) used as rule by this event bridge lambda target view; the target may be shared elsewhere in the model.         |
| `targetRow` → `EventBridgeTarget` | reference, [1]        | References the event bridge target element(s) used as target row by this event bridge lambda target view; the target may be shared elsewhere in the model. |
| `function` → `AwsLambdaFunction`  | reference, [1]        | References the aws lambda function element(s) used as function by this event bridge lambda target view; the target may be shared elsewhere in the model.   |
| `permission` → `LambdaPermission` | reference, [?]        | References the lambda permission element(s) used as permission by this event bridge lambda target view; the target may be shared elsewhere in the model.   |

## `SnsLambdaSubscriptionView`

Represents sns lambda subscription view in the PSM vocabulary. It specializes `AwsRelationshipView` with the details needed for this modeling concern.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                       | Kind and multiplicity | Meaning in the model                                                                                                                                   |
| ---------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `topic` → `SnsTopic`               | reference, [1]        | References the sns topic element(s) used as topic by this sns lambda subscription view; the target may be shared elsewhere in the model.               |
| `function` → `AwsLambdaFunction`   | reference, [1]        | References the aws lambda function element(s) used as function by this sns lambda subscription view; the target may be shared elsewhere in the model.  |
| `subscription` → `SnsSubscription` | reference, [1]        | References the sns subscription element(s) used as subscription by this sns lambda subscription view; the target may be shared elsewhere in the model. |
| `permission` → `LambdaPermission`  | reference, [?]        | References the lambda permission element(s) used as permission by this sns lambda subscription view; the target may be shared elsewhere in the model.  |

## `SqsLambdaEventSourceView`

Represents sqs lambda event source view in the PSM vocabulary. It specializes `AwsRelationshipView` with the details needed for this modeling concern.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                              | Kind and multiplicity | Meaning in the model                                                                                                                                             |
| ----------------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `queue` → `SqsQueue`                      | reference, [1]        | References the sqs queue element(s) used as queue by this sqs lambda event source view; the target may be shared elsewhere in the model.                         |
| `function` → `AwsLambdaFunction`          | reference, [1]        | References the aws lambda function element(s) used as function by this sqs lambda event source view; the target may be shared elsewhere in the model.            |
| `mapping` → `SqsLambdaEventSourceMapping` | reference, [1]        | References the sqs lambda event source mapping element(s) used as mapping by this sqs lambda event source view; the target may be shared elsewhere in the model. |

## `StepFunctionEventBridgeTargetView`

Represents step function event bridge target view in the PSM vocabulary. It specializes `AwsRelationshipView` with the details needed for this modeling concern.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                                | Kind and multiplicity | Meaning in the model                                                                                                                                                         |
| ------------------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rule` → `EventBridgeRule`                  | reference, [1]        | References the event bridge rule element(s) used as rule by this step function event bridge target view; the target may be shared elsewhere in the model.                    |
| `stateMachine` → `StepFunctionStateMachine` | reference, [1]        | References the step function state machine element(s) used as state machine by this step function event bridge target view; the target may be shared elsewhere in the model. |
| `targetRow` → `EventBridgeTarget`           | reference, [1]        | References the event bridge target element(s) used as target row by this step function event bridge target view; the target may be shared elsewhere in the model.            |

## `S3LambdaNotificationView`

Represents s3 lambda notification view in the PSM vocabulary. It specializes `AwsRelationshipView` with the details needed for this modeling concern.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                      | Kind and multiplicity | Meaning in the model                                                                                                                                 |
| --------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bucket` → `S3Bucket`             | reference, [1]        | References the s3 bucket element(s) used as bucket by this s3 lambda notification view; the target may be shared elsewhere in the model.             |
| `function` → `AwsLambdaFunction`  | reference, [1]        | References the aws lambda function element(s) used as function by this s3 lambda notification view; the target may be shared elsewhere in the model. |
| `rule` → `S3NotificationRule`     | reference, [1]        | References the s3 notification rule element(s) used as rule by this s3 lambda notification view; the target may be shared elsewhere in the model.    |
| `permission` → `LambdaPermission` | reference, [1]        | References the lambda permission element(s) used as permission by this s3 lambda notification view; the target may be shared elsewhere in the model. |

## `S3QueueNotificationView`

Represents s3 queue notification view in the PSM vocabulary. It specializes `AwsRelationshipView` with the details needed for this modeling concern.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                     | Kind and multiplicity | Meaning in the model                                                                                                                                 |
| -------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bucket` → `S3Bucket`            | reference, [1]        | References the s3 bucket element(s) used as bucket by this s3 queue notification view; the target may be shared elsewhere in the model.              |
| `queue` → `SqsQueue`             | reference, [1]        | References the sqs queue element(s) used as queue by this s3 queue notification view; the target may be shared elsewhere in the model.               |
| `rule` → `S3NotificationRule`    | reference, [1]        | References the s3 notification rule element(s) used as rule by this s3 queue notification view; the target may be shared elsewhere in the model.     |
| `queuePolicy` → `SqsQueuePolicy` | reference, [?]        | References the sqs queue policy element(s) used as queue policy by this s3 queue notification view; the target may be shared elsewhere in the model. |

## `S3TopicNotificationView`

Represents s3 topic notification view in the PSM vocabulary. It specializes `AwsRelationshipView` with the details needed for this modeling concern.

Direct supertypes: `AwsRelationshipView`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                     | Kind and multiplicity | Meaning in the model                                                                                                                                 |
| -------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bucket` → `S3Bucket`            | reference, [1]        | References the s3 bucket element(s) used as bucket by this s3 topic notification view; the target may be shared elsewhere in the model.              |
| `topic` → `SnsTopic`             | reference, [1]        | References the sns topic element(s) used as topic by this s3 topic notification view; the target may be shared elsewhere in the model.               |
| `rule` → `S3NotificationRule`    | reference, [1]        | References the s3 notification rule element(s) used as rule by this s3 topic notification view; the target may be shared elsewhere in the model.     |
| `topicPolicy` → `SnsTopicPolicy` | reference, [?]        | References the sns topic policy element(s) used as topic policy by this s3 topic notification view; the target may be shared elsewhere in the model. |
