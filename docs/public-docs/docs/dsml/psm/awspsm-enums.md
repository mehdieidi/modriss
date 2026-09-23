# AWS PSM enumerations

The AWS PSM uses enumerations wherever a deployment decision has a controlled AWS or CloudFormation vocabulary. These values are part of the abstract syntax. They prevent a model from silently changing meaning through a spelling variation and give EVL, ETL, and generation a stable value to inspect.

Source: `mde/metamodels/psm/awspsm-enums.emf`.

The values below describe the choices available to the model. A value can still be inappropriate in a particular context. For example, a `PRIVATE` API endpoint needs a compatible network and resource design, while `SNAPSHOT` only makes sense for resource types that support snapshots.

## Deployment and CloudFormation

### `AwsEnvironmentClass`

Names the operational character of an AWS stage. The value is used to distinguish a production-like stage from development, testing, staging, disaster recovery, or an isolated sandbox.

| Literal   | Meaning                                                                                 |
| --------- | --------------------------------------------------------------------------------------- |
| `DEV`     | A development stage used for active design and implementation work.                     |
| `TEST`    | A stage intended for test execution and verification.                                   |
| `STAGING` | A pre-production stage used to exercise a release in a production-like setting.         |
| `PROD`    | The production stage that serves live business operation.                               |
| `SANDBOX` | An isolated experimental stage whose contents are not treated as a release environment. |
| `DR`      | A disaster-recovery stage used to support restoration or continuity planning.           |

### `AwsPartition`

Selects the AWS partition in which ARNs and service endpoints are interpreted.

| Literal      | Meaning                                                          |
| ------------ | ---------------------------------------------------------------- |
| `AWS`        | The standard commercial AWS partition.                           |
| `AWS_US_GOV` | The AWS GovCloud partition for United States government regions. |
| `AWS_CN`     | The AWS China partition.                                         |

### `CloudFormationDeletionPolicy`

Controls what CloudFormation should do with a resource when the generated stack removes it.

| Literal                   | Meaning                                                                             |
| ------------------------- | ----------------------------------------------------------------------------------- |
| `DELETE`                  | Remove the resource with the stack.                                                 |
| `RETAIN`                  | Keep the resource after stack removal.                                              |
| `RETAIN_EXCEPT_ON_CREATE` | Retain an existing resource while allowing cleanup when its initial creation fails. |
| `SNAPSHOT`                | Create a snapshot before deletion where the resource type supports that behavior.   |

### `CloudFormationUpdateReplacePolicy`

Controls what happens to the previous physical resource when an update requires replacement.

| Literal    | Meaning                                                         |
| ---------- | --------------------------------------------------------------- |
| `DELETE`   | Delete the replaced resource.                                   |
| `RETAIN`   | Preserve the replaced resource.                                 |
| `SNAPSHOT` | Snapshot the replaced resource before deletion where supported. |

### `Decision`

Records the state of an explicit deployment decision. It is used when a provider setting needs review or when the generator owns the final value.

| Literal           | Meaning                                                      |
| ----------------- | ------------------------------------------------------------ |
| `UNDECIDED`       | No decision has been made yet.                               |
| `REQUIRED`        | The decision is required before the design can proceed.      |
| `NOT_REQUIRED`    | The concern was considered and does not apply.               |
| `ACCEPTED`        | The proposed decision was accepted.                          |
| `NEEDS_REVIEW`    | A decision exists but requires another review.               |
| `GENERATOR_OWNED` | The generator is responsible for choosing the emitted value. |

### `SamCapability`

Lists the deployment capabilities that a SAM or CloudFormation deployment may need to acknowledge.

| Literal                  | Meaning                                                                   |
| ------------------------ | ------------------------------------------------------------------------- |
| `CAPABILITY_IAM`         | The template creates or changes IAM resources.                            |
| `CAPABILITY_NAMED_IAM`   | The template creates or changes IAM resources with custom names.          |
| `CAPABILITY_AUTO_EXPAND` | The deployment allows macros or nested expansion to process the template. |

## Lambda

### `PackageType`

Selects how Lambda function code is packaged.

| Literal | Meaning                                          |
| ------- | ------------------------------------------------ |
| `ZIP`   | The function is deployed from a ZIP archive.     |
| `IMAGE` | The function is deployed from a container image. |

### `LambdaArchitecture`

Selects the instruction-set architecture for the Lambda runtime.

| Literal  | Meaning                         |
| -------- | ------------------------------- |
| `X86_64` | The x86-64 Lambda architecture. |
| `ARM64`  | The ARM64 Lambda architecture.  |

### `LambdaTracingMode`

Controls the X-Ray tracing behavior of a Lambda function.

| Literal        | Meaning                                                                    |
| -------------- | -------------------------------------------------------------------------- |
| `ACTIVE`       | Trace segments are actively produced for invocations.                      |
| `PASS_THROUGH` | The function follows the tracing decision carried by the incoming request. |
| `DISABLED`     | Tracing is disabled for the function.                                      |

### `LambdaRuntimeManagementMode`

States who controls updates to the Lambda runtime.

| Literal           | Meaning                                                        |
| ----------------- | -------------------------------------------------------------- |
| `AUTO`            | AWS manages runtime updates automatically.                     |
| `FUNCTION_UPDATE` | Runtime updates are applied with function updates.             |
| `MANUAL`          | Runtime updates are held for an explicit operational decision. |

### `LambdaSnapStartApplyOn`

States which published Lambda versions receive SnapStart treatment.

| Literal              | Meaning                                     |
| -------------------- | ------------------------------------------- |
| `NONE`               | SnapStart is not applied.                   |
| `PUBLISHED_VERSIONS` | SnapStart is applied to published versions. |

### `LambdaInvokeMode`

Selects how a Lambda response is delivered.

| Literal           | Meaning                                                    |
| ----------------- | ---------------------------------------------------------- |
| `BUFFERED`        | The invocation uses the normal buffered response behavior. |
| `RESPONSE_STREAM` | The function can stream its response to the caller.        |

### `LambdaFunctionUrlAuthType`

Defines the authentication mode of a Lambda function URL.

| Literal   | Meaning                                       |
| --------- | --------------------------------------------- |
| `NONE`    | The URL does not require AWS authentication.  |
| `AWS_IAM` | The caller must authenticate through AWS IAM. |

### `LambdaRecursiveLoopMode`

Describes the intended response to recursive Lambda invocation patterns.

| Literal       | Meaning                                         |
| ------------- | ----------------------------------------------- |
| `ALLOW`       | Recursive invocation is allowed.                |
| `TERMINATE`   | The recursive loop should be terminated.        |
| `UNSPECIFIED` | The model leaves the loop behavior unspecified. |

### `LambdaEventSourceKind`

Identifies the kind of event source mapped to a Lambda function.

| Literal              | Meaning                                                     |
| -------------------- | ----------------------------------------------------------- |
| `SQS`                | Amazon SQS delivers messages to the function.               |
| `DYNAMODB_STREAM`    | A DynamoDB stream supplies change records.                  |
| `KINESIS_STREAM`     | An Amazon Kinesis stream supplies records.                  |
| `MSK`                | Amazon Managed Streaming for Apache Kafka supplies records. |
| `SELF_MANAGED_KAFKA` | A self-managed Kafka cluster supplies records.              |
| `DOCUMENTDB`         | Amazon DocumentDB supplies change events.                   |
| `MQ`                 | An Amazon MQ broker supplies messages.                      |
| `OTHER`              | Another supported event-source kind is used.                |

### `StartingPosition`

Selects where a stream event source begins reading records.

| Literal        | Meaning                                                            |
| -------------- | ------------------------------------------------------------------ |
| `TRIM_HORIZON` | Start from the oldest record still available.                      |
| `LATEST`       | Start from records arriving after the event-source mapping begins. |
| `AT_TIMESTAMP` | Start at a declared timestamp.                                     |

## API Gateway

### `ApiGatewayKind`

Identifies the API Gateway family used by the resource.

| Literal         | Meaning                       |
| --------------- | ----------------------------- |
| `HTTP_API`      | An API Gateway HTTP API.      |
| `REST_API`      | An API Gateway REST API.      |
| `WEBSOCKET_API` | An API Gateway WebSocket API. |

### `ApiGatewayEndpointType`

Describes where an API Gateway endpoint is served.

| Literal    | Meaning                                                       |
| ---------- | ------------------------------------------------------------- |
| `REGIONAL` | The endpoint is served within a selected AWS region.          |
| `EDGE`     | The endpoint uses an edge-optimized distribution.             |
| `PRIVATE`  | The endpoint is reachable through a private network boundary. |

### `ApiGatewayAuthorizationType`

Selects how an API Gateway route authorizes its caller.

| Literal              | Meaning                                               |
| -------------------- | ----------------------------------------------------- |
| `NONE`               | No API Gateway authorization mechanism is attached.   |
| `AWS_IAM`            | AWS IAM signs and authorizes the request.             |
| `JWT`                | A JSON Web Token is validated.                        |
| `COGNITO_USER_POOLS` | A Cognito user pool authorizes the caller.            |
| `CUSTOM_LAMBDA`      | A Lambda authorizer makes the authorization decision. |
| `API_KEY`            | An API key is required for the route.                 |

### `ApiGatewayIntegrationType`

States how API Gateway connects a route to its backend.

| Literal      | Meaning                                                          |
| ------------ | ---------------------------------------------------------------- |
| `AWS_PROXY`  | API Gateway passes the request through an AWS proxy integration. |
| `AWS`        | API Gateway calls an AWS service integration.                    |
| `HTTP_PROXY` | API Gateway proxies the request to an HTTP endpoint.             |
| `HTTP`       | API Gateway uses an HTTP integration with mapping behavior.      |
| `MOCK`       | API Gateway returns a configured mock response.                  |

### `ApiGatewayHttpMethod`

Provides the HTTP methods available for API Gateway routes.

| Literal   | Meaning                                             |
| --------- | --------------------------------------------------- |
| `GET`     | Retrieve a representation.                          |
| `POST`    | Submit or create a representation.                  |
| `PUT`     | Replace a representation.                           |
| `PATCH`   | Partially update a representation.                  |
| `DELETE`  | Remove a representation.                            |
| `OPTIONS` | Ask for supported communication options.            |
| `HEAD`    | Retrieve response metadata without a response body. |
| `ANY`     | Accept any supported method at the route.           |

## EventBridge and integration delivery

### `EventBridgeHttpMethod`

Lists the methods allowed for an EventBridge HTTP invocation.

| Literal   | Meaning                                          |
| --------- | ------------------------------------------------ |
| `GET`     | Read from the destination endpoint.              |
| `POST`    | Submit a new request body.                       |
| `PUT`     | Replace the destination representation.          |
| `PATCH`   | Partially update the destination representation. |
| `DELETE`  | Request deletion at the destination.             |
| `HEAD`    | Request destination metadata without a body.     |
| `OPTIONS` | Request the destination's supported options.     |

### `EventBridgeTargetKind`

Names the kind of AWS destination reached by an EventBridge target.

| Literal              | Meaning                                    |
| -------------------- | ------------------------------------------ |
| `LAMBDA`             | Invoke an AWS Lambda function.             |
| `SQS`                | Send a message to an SQS queue.            |
| `SNS`                | Publish to an SNS topic.                   |
| `STEP_FUNCTIONS`     | Start a Step Functions state machine.      |
| `API_DESTINATION`    | Invoke an EventBridge API destination.     |
| `EVENT_BUS`          | Put events on another event bus.           |
| `LOG_GROUP`          | Send events to a CloudWatch log group.     |
| `OTHER_AWS_RESOURCE` | Reach another supported AWS resource kind. |

### `EventBridgeConnectionAuthorizationType`

Selects how an EventBridge connection authenticates an HTTP destination.

| Literal                    | Meaning                                          |
| -------------------------- | ------------------------------------------------ |
| `API_KEY`                  | Send an API key.                                 |
| `BASIC`                    | Use HTTP basic authentication.                   |
| `OAUTH_CLIENT_CREDENTIALS` | Obtain a token through OAuth client credentials. |

### `SqsQueueType`

Selects the delivery and ordering model of an SQS queue.

| Literal    | Meaning                                                     |
| ---------- | ----------------------------------------------------------- |
| `STANDARD` | Use standard queue delivery with at-least-once behavior.    |
| `FIFO`     | Use FIFO delivery and its ordering and deduplication rules. |

### `SnsProtocol`

Identifies the protocol or endpoint family used by an SNS subscription.

| Literal       | Meaning                                    |
| ------------- | ------------------------------------------ |
| `LAMBDA`      | Deliver to a Lambda function.              |
| `SQS`         | Deliver to an SQS queue.                   |
| `HTTP`        | Deliver to an HTTP endpoint.               |
| `HTTPS`       | Deliver to an HTTPS endpoint.              |
| `EMAIL`       | Deliver through email.                     |
| `SMS`         | Deliver through SMS.                       |
| `FIREHOSE`    | Deliver to a Kinesis Data Firehose stream. |
| `APPLICATION` | Deliver to an application endpoint.        |

### `SnsFilterPolicyScope`

States where SNS evaluates subscription filter attributes.

| Literal              | Meaning                      |
| -------------------- | ---------------------------- |
| `MESSAGE_ATTRIBUTES` | Evaluate message attributes. |
| `MESSAGE_BODY`       | Evaluate the message body.   |

### `StepFunctionType`

Selects the execution class of a Step Functions state machine.

| Literal    | Meaning                 |
| ---------- | ----------------------- |
| `STANDARD` | Use Standard Workflows. |
| `EXPRESS`  | Use Express Workflows.  |

## DynamoDB and S3

### `DynamoDbBillingMode`

Selects how DynamoDB capacity is charged and configured.

| Literal           | Meaning                              |
| ----------------- | ------------------------------------ |
| `PAY_PER_REQUEST` | Use on-demand capacity.              |
| `PROVISIONED`     | Use explicitly provisioned capacity. |

### `DynamoDbAttributeType`

Names the DynamoDB scalar type used by a key attribute definition.

| Literal | Meaning              |
| ------- | -------------------- |
| `S`     | A string attribute.  |
| `N`     | A numeric attribute. |
| `B`     | A binary attribute.  |

### `DynamoDbKeyType`

Identifies the role of a key-schema element.

| Literal | Meaning                 |
| ------- | ----------------------- |
| `HASH`  | The partition-key role. |
| `RANGE` | The sort-key role.      |

### `DynamoDbProjectionType`

Selects which attributes an index projects.

| Literal     | Meaning                                           |
| ----------- | ------------------------------------------------- |
| `ALL`       | Project all table attributes.                     |
| `KEYS_ONLY` | Project only key attributes.                      |
| `INCLUDE`   | Project the explicitly listed non-key attributes. |

### `DynamoDbStreamViewType`

Selects the record image exposed by a DynamoDB stream.

| Literal              | Meaning                                     |
| -------------------- | ------------------------------------------- |
| `KEYS_ONLY`          | Include only the changed item's keys.       |
| `NEW_IMAGE`          | Include the item's image after the change.  |
| `OLD_IMAGE`          | Include the item's image before the change. |
| `NEW_AND_OLD_IMAGES` | Include both images.                        |

### `DynamoDbTableClass`

Selects the storage class used by the DynamoDB table.

| Literal                      | Meaning                                |
| ---------------------------- | -------------------------------------- |
| `STANDARD`                   | Use the standard DynamoDB table class. |
| `STANDARD_INFREQUENT_ACCESS` | Use the infrequent-access table class. |

### `S3VersioningStatus`

States the versioning status of an S3 bucket.

| Literal     | Meaning                                                              |
| ----------- | -------------------------------------------------------------------- |
| `ENABLED`   | Object versioning is enabled.                                        |
| `SUSPENDED` | New versioning is suspended while existing versions remain relevant. |

### `S3BlockPublicAccessMode`

Describes the public-access posture of an S3 bucket.

| Literal                    | Meaning                                                                               |
| -------------------------- | ------------------------------------------------------------------------------------- |
| `STRICT_BLOCK_ALL`         | Apply the full block-public-access posture.                                           |
| `CUSTOM`                   | Apply an explicitly selected combination of public-access controls.                   |
| `DISABLED_NOT_RECOMMENDED` | Leave block-public-access controls disabled, with the model making that risk visible. |

## Cognito, IAM, and configuration values

### `CognitoMfaConfiguration`

States the multi-factor authentication requirement for a Cognito user pool.

| Literal    | Meaning                                           |
| ---------- | ------------------------------------------------- |
| `OFF`      | MFA is disabled.                                  |
| `ON`       | MFA is required.                                  |
| `OPTIONAL` | MFA is available but not required for every user. |

### `IamEffect`

States the effect of an IAM statement.

| Literal | Meaning                    |
| ------- | -------------------------- |
| `ALLOW` | Permit the matched action. |
| `DENY`  | Refuse the matched action. |

### `ParameterType`

Selects the AWS Systems Manager parameter type.

| Literal         | Meaning                              |
| --------------- | ------------------------------------ |
| `STRING`        | Store a plain string value.          |
| `STRING_LIST`   | Store a comma-separated string list. |
| `SECURE_STRING` | Store an encrypted secure string.    |

### `SsmParameterTier`

Selects the Systems Manager Parameter Store tier.

| Literal               | Meaning                                      |
| --------------------- | -------------------------------------------- |
| `STANDARD`            | Use the standard tier.                       |
| `ADVANCED`            | Use the advanced tier.                       |
| `INTELLIGENT_TIERING` | Let Parameter Store use intelligent tiering. |

### `ValueSourceKind`

Identifies where a generated value comes from.

| Literal                     | Meaning                                    |
| --------------------------- | ------------------------------------------ |
| `PLAINTEXT`                 | Emit a literal plain value.                |
| `CLOUDFORMATION_REF`        | Read another template value through `Ref`. |
| `CLOUDFORMATION_GETATT`     | Read an attribute through `Fn::GetAtt`.    |
| `CLOUDFORMATION_SUB`        | Construct a value through `Fn::Sub`.       |
| `SSM_PARAMETER_REFERENCE`   | Read a normal SSM parameter.               |
| `SSM_SECURE_REFERENCE`      | Read a secure SSM parameter.               |
| `SECRETS_MANAGER_REFERENCE` | Read a Secrets Manager value.              |
| `DYNAMIC_REFERENCE`         | Use a CloudFormation dynamic reference.    |
| `IMPORT_VALUE`              | Read an exported value from another stack. |
| `RESOURCE_ATTRIBUTE`        | Use an attribute of an AWS resource.       |
| `LIST`                      | Construct a list value.                    |
| `MAP`                       | Construct a map value.                     |

### `ValueType`

States the abstract type expected from a generated value expression.

| Literal   | Meaning                                                      |
| --------- | ------------------------------------------------------------ |
| `STRING`  | A text value.                                                |
| `NUMBER`  | A numeric value.                                             |
| `BOOLEAN` | A Boolean value.                                             |
| `LIST`    | A list value.                                                |
| `MAP`     | A map value.                                                 |
| `ARN`     | An AWS ARN.                                                  |
| `NAME`    | A resource or configuration name.                            |
| `URL`     | A URL.                                                       |
| `JSON`    | A JSON document or fragment.                                 |
| `SECRET`  | A protected value that should be handled as secret material. |

## Observability

### `CloudWatchComparisonOperator`

Selects how a CloudWatch metric is compared with a threshold.

| Literal                                           | Meaning                                             |
| ------------------------------------------------- | --------------------------------------------------- |
| `GREATER_THAN_OR_EQUAL_TO_THRESHOLD`              | Alarm when the metric is at least the threshold.    |
| `GREATER_THAN_THRESHOLD`                          | Alarm when the metric exceeds the threshold.        |
| `LESS_THAN_THRESHOLD`                             | Alarm when the metric is below the threshold.       |
| `LESS_THAN_OR_EQUAL_TO_THRESHOLD`                 | Alarm when the metric is at or below the threshold. |
| `LESS_THAN_LOWER_OR_GREATER_THAN_UPPER_THRESHOLD` | Alarm outside a lower and upper band.               |
| `LESS_THAN_LOWER_THRESHOLD`                       | Alarm below the lower threshold.                    |
| `GREATER_THAN_UPPER_THRESHOLD`                    | Alarm above the upper threshold.                    |

### `LogGroupClass`

Selects the CloudWatch log-group storage class.

| Literal             | Meaning                                    |
| ------------------- | ------------------------------------------ |
| `STANDARD`          | Use the standard log-group class.          |
| `INFREQUENT_ACCESS` | Use the infrequent-access log-group class. |
| `DELIVERY`          | Use the delivery-oriented log-group class. |
