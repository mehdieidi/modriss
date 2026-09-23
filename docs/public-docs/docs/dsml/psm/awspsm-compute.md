# AWS Lambda resources

Lambda classes describe code packaging, runtime, environment, event sources, permissions, destinations, layers, URLs, tracing, and operational safeguards.

Source: `mde/metamodels/psm/awspsm-compute.emf`.

## `AwsLambdaFunction`

The AWS resource realization of a serverless compute function. It binds code packaging, runtime, memory, timeout, environment, event sources, permissions, destinations, networking, logging, and tracing to Lambda.

Direct supertypes: `AwsResource`, `S3NotificationDestination`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                      | Type and multiplicity             | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                                                                                                |
| ------------------------------ | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `functionName`                 | `String` [1]                      | The function name that identifies this aws lambda function in the model and its generated AWS configuration. Transformation role: ETL rule `Function2AwsLambdaFunction` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `AwsLambdaFunction`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/naming.eol`.                                                                                                                                                                     | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `AwsLambdaFunctionExample`.               |
| `descriptionText`              | `String` [1]                      | The provider-facing descriptive text emitted for this resource. It gives an operator context in the generated template and console without carrying runtime behavior. Within `AwsLambdaFunction`, it applies to this specific element and its role in the surrounding model.                                                                                                                                                                                                                                                                                                                                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`.                      |
| `memorySizeMb`                 | `Integer` [1]                     | The memory allocated to each function execution, which also influences available CPU. Semantic validation: `LambdaMemoryRange` (lambda memory range) in `mde/validation/psm/rules/compute.evl` the value must remain absent in this modeling situation. Transformation role: ETL rule `Function2AwsLambdaFunction` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `AwsLambdaFunction`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                 | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                           |
| `timeoutSeconds`               | `Integer` [1]                     | The numeric value used for timeout seconds on this aws lambda function. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `30`.                                          |
| `ephemeralStorageMb`           | `Integer` [1]                     | The size of writable temporary storage mounted at `/tmp` for each execution environment. Semantic validation: `LambdaEphemeralStorageRange` (lambda ephemeral storage range) in `mde/validation/psm/rules/compute.evl` the value must remain absent in this modeling situation. Transformation role: ETL rule `Function2AwsLambdaFunction` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `AwsLambdaFunction`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                         | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                           |
| `reservedConcurrentExecutions` | `Integer` [1]                     | The numeric value used for reserved concurrent executions on this aws lambda function. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                           |
| `autoPublishAlias`             | `String` [1]                      | The alias name that SAM updates automatically when publishing a new function version.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Aws Lambda Function Auto Publish Alias`. |
| `publishVersion`               | `Boolean` [1]                     | The boolean decision for publish version on this aws lambda function. It keeps an important design choice explicit for review and transformation.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Either `true` or `false`. Example: `false`.                                                                                                |
| `codeSigningDecision`          | `Decision` [1]                    | The controlled value used for code signing decision on this aws lambda function. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. Semantic validation: `CodeSigningDecisionHonored` (code signing decision honored) in `mde/validation/psm/rules/compute.evl` the feature participates in a semantic validation condition. Transformation role: ETL rule `Function2AwsLambdaFunction` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `AwsLambdaFunction`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`. | Exactly one of: `UNDECIDED`, `REQUIRED`, `NOT_REQUIRED`, `ACCEPTED`, `NEEDS_REVIEW`, `GENERATOR_OWNED`. Example: `UNDECIDED`.              |
| `architecture`                 | `LambdaArchitecture` [1]          | The processor architecture expected by the function code and execution environment. Transformation role: ETL rule `Function2AwsLambdaFunction` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `AwsLambdaFunction`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                        | Exactly one of: `X86_64`, `ARM64`. Example: `X86_64`.                                                                                      |
| `packageType`                  | `PackageType` [1]                 | The controlled value used for package type on this aws lambda function. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Exactly one of: `ZIP`, `IMAGE`. Example: `ZIP`.                                                                                            |
| `recursiveLoopMode`            | `LambdaRecursiveLoopMode` [1]     | Lambda's response when it detects a recursive invocation loop. Transformation role: ETL rule `Function2AwsLambdaFunction` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `AwsLambdaFunction`.                                                                                                                                                                                                                                                                                                                                                                                                                                               | Exactly one of: `ALLOW`, `TERMINATE`, `UNSPECIFIED`. Example: `ALLOW`.                                                                     |
| `snapStartApplyOn`             | `LambdaSnapStartApplyOn` [1]      | The published function versions for which Lambda SnapStart should create an initialized snapshot. Transformation role: ETL rule `Function2AwsLambdaFunction` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `AwsLambdaFunction`. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/validation.eol`.                                                                                                                                                                            | Exactly one of: `NONE`, `PUBLISHED_VERSIONS`. Example: `NONE`.                                                                             |
| `runtimeManagementMode`        | `LambdaRuntimeManagementMode` [1] | The controlled value used for runtime management mode on this aws lambda function. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Exactly one of: `AUTO`, `FUNCTION_UPDATE`, `MANUAL`. Example: `AUTO`.                                                                      |

### Relationships

| Relationship                                       | Kind and multiplicity                       | Meaning in the model                                                                                                                                                                                                                                                                                                                          |
| -------------------------------------------------- | ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `code` → `LambdaCodeConfig`                        | containment, [1]                            | The zip or image code configuration owned by the Lambda function.                                                                                                                                                                                                                                                                             |
| `environment` → `LambdaEnvironmentVariable`        | containment, [*]                            | Environment variables owned by the Lambda function.                                                                                                                                                                                                                                                                                           |
| `deadLetterConfig` → `LambdaDeadLetterConfig`      | containment, [?]                            | The `deadLetterConfig` containment on `AwsLambdaFunction` attaches the configuration record represented by dead letter config. The `LambdaDeadLetterConfig` objects are owned by `AwsLambdaFunction` and remain part of its model subtree.                                                                                                    |
| `tracing` → `LambdaTracingConfig`                  | containment, [?]                            | The `tracing` containment on `AwsLambdaFunction` attaches the tracing configuration for this executable resource. The `LambdaTracingConfig` objects are owned by `AwsLambdaFunction` and remain part of its model subtree.                                                                                                                    |
| `logging` → `LambdaLoggingConfig`                  | containment, [?]                            | The `logging` containment on `AwsLambdaFunction` attaches the state-machine logging configuration. The `LambdaLoggingConfig` objects are owned by `AwsLambdaFunction` and remain part of its model subtree.                                                                                                                                   |
| `fileSystemConfigs` → `LambdaFileSystemConfig`     | containment, [*]                            | The `fileSystemConfigs` containment on `AwsLambdaFunction` attaches the configuration record represented by file system configs. The `LambdaFileSystemConfig` objects are owned by `AwsLambdaFunction` and remain part of its model subtree.                                                                                                  |
| `samEvents` → `SamFunctionEvent`                   | containment, [*]; opposite `targetFunction` | The `samEvents` containment on `AwsLambdaFunction` connects this element to the event or message path represented by sam events. The `SamFunctionEvent` objects are owned by `AwsLambdaFunction` and remain part of its model subtree. Its opposite `targetFunction` exposes the same connection from the target side.                        |
| `vpcConfig` → `VpcAttachmentConfig`                | containment, [?]                            | The `vpcConfig` containment on `AwsLambdaFunction` attaches the configuration record represented by vpc config. The `VpcAttachmentConfig` objects are owned by `AwsLambdaFunction` and remain part of its model subtree.                                                                                                                      |
| `layers` → `LambdaLayerVersion`                    | reference, [*]                              | Reusable Lambda layers attached to the function.                                                                                                                                                                                                                                                                                              |
| `permissions` → `LambdaPermission`                 | containment, [*]; opposite `function`       | Resource-based Lambda permissions owned by the function.                                                                                                                                                                                                                                                                                      |
| `versions` → `LambdaVersion`                       | containment, [*]; opposite `function`       | Published versions owned by the Lambda function.                                                                                                                                                                                                                                                                                              |
| `aliases` → `LambdaAlias`                          | containment, [*]; opposite `function`       | The `aliases` containment on `AwsLambdaFunction` keeps the named aliases that make the KMS key addressable. The `LambdaAlias` objects are owned by `AwsLambdaFunction` and remain part of its model subtree. Its opposite `function` exposes the same connection from the target side.                                                        |
| `eventInvokeConfigs` → `LambdaEventInvokeConfig`   | containment, [*]; opposite `function`       | The `eventInvokeConfigs` containment on `AwsLambdaFunction` attaches the configuration record represented by event invoke configs. The `LambdaEventInvokeConfig` objects are owned by `AwsLambdaFunction` and remain part of its model subtree. Its opposite `function` exposes the same connection from the target side.                     |
| `eventSourceMappings` → `LambdaEventSourceMapping` | containment, [*]; opposite `function`       | The `eventSourceMappings` containment on `AwsLambdaFunction` connects this element to the event or message path represented by event source mappings. The `LambdaEventSourceMapping` objects are owned by `AwsLambdaFunction` and remain part of its model subtree. Its opposite `function` exposes the same connection from the target side. |
| `logGroup` → `CloudWatchLogGroup`                  | reference, [1]                              | The `logGroup` reference on `AwsLambdaFunction` selects the CloudWatch log group that receives this resource's records. A `CloudWatchLogGroup` can remain independently owned and can participate in other parts of the model.                                                                                                                |
| `role` → `IamRole`                                 | reference, [1]                              | The `role` reference on `AwsLambdaFunction` attaches the execution role that grants the resource its AWS permissions. An `IamRole` can remain independently owned and can participate in other parts of the model.                                                                                                                            |
| `kmsKey` → `KmsKey`                                | reference, [?]                              | The `kmsKey` reference on `AwsLambdaFunction` selects the KMS key used for provider-side encryption. A `KmsKey` can remain independently owned and can participate in other parts of the model.                                                                                                                                               |
| `functionUrl` → `LambdaFunctionUrl`                | containment, [?]; opposite `function`       | The optional HTTPS endpoint owned by the Lambda function.                                                                                                                                                                                                                                                                                     |
| `codeSigningConfig` → `CodeSigningConfig`          | reference, [?]                              | The `codeSigningConfig` reference on `AwsLambdaFunction` attaches the configuration record represented by code signing config. A `CodeSigningConfig` can remain independently owned and can participate in other parts of the model.                                                                                                          |

## `LambdaCodeConfig`

`LambdaCodeConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda code config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `LambdaZipCodeConfig`

`LambdaZipCodeConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda zip code config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `LambdaCodeConfig`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute           | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                   | Accepted values and example                                                                                                                |
| ------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `codeUri`           | `String` [1]          | The local path or S3 location of the zip deployment package.                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `https://example.com/orders`.             |
| `s3Bucket`          | `String` [1]          | The bucket containing the zip deployment package when S3-based code location is selected. Semantic validation: `ZipCodeHasExactlyOneLocation` (zip code has exactly one location) in `mde/validation/psm/rules/compute.evl` the value must be present and non-blank. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Zip Code Config S3 Bucket`.       |
| `s3Key`             | `String` [1]          | The S3 object key of the deployment package.                                                                                                                                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`.                                |
| `s3ObjectVersion`   | `String` [1]          | The S3 object version that fixes the package to an immutable revision.                                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `1.0`.                                    |
| `inlineZipFile`     | `String` [1]          | Inline source text for the small-function packaging form supported by CloudFormation.                                                                                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Zip Code Config Inline Zip File`. |
| `runtimeIdentifier` | `String` [1]          | The Lambda runtime identifier used for a zip package.                                                                                                                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                              |
| `handler`           | `String` [1]          | The runtime entry handler invoked for a zip package.                                                                                                                                                                                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `handlers/orders.handle`.                 |

### Relationships

This class declares no direct relationships.

## `LambdaImageCodeConfig`

`LambdaImageCodeConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda image code config. Its declaration gives the concept a precise home through image config. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `LambdaCodeConfig`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute  | Type and multiplicity | What it captures and why it exists                   | Accepted values and example                                                                                                    |
| ---------- | --------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `imageUri` | `String` [1]          | The container image URI supplying the function code. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `https://example.com/orders`. |

### Relationships

| Relationship                        | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                               |
| ----------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `imageConfig` → `LambdaImageConfig` | containment, [?]      | The `imageConfig` containment on `LambdaImageCodeConfig` attaches the configuration record represented by image config. The `LambdaImageConfig` objects are owned by `LambdaImageCodeConfig` and remain part of its model subtree. |

## `LambdaImageConfig`

`LambdaImageConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda image config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute          | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                            | Accepted values and example                                                                                                               |
| ------------------ | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `command`          | `String` [*]          | Arguments that override the container image's default command. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | A collection of values. Example: [`Lambda Image Config Command`, `Lambda Image Config Command-2`].                                        |
| `entryPoint`       | `String` [*]          | Container entry-point arguments that override the image defaults. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                        | A collection of values. Example: [`Lambda Image Config Entry Point`, `Lambda Image Config Entry Point-2`].                                |
| `workingDirectory` | `String` [1]          | The directory from which Lambda starts the container image.                                                                                                                                                                                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Image Config Working Directory`. |

### Relationships

This class declares no direct relationships.

## `LambdaEnvironmentVariable`

`LambdaEnvironmentVariable` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda environment variable. Its declaration gives the concept a precise home through value. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                 | Accepted values and example                                                                                                          |
| --------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| `variableName`  | `String` [1]          | The variable name that identifies this lambda environment variable in the model and its generated AWS configuration.                                                                                                                                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `LambdaEnvironmentVariableExample`. |
| `stageSpecific` | `Boolean` [1]         | The boolean decision for stage specific on this lambda environment variable. It keeps an important design choice explicit for review and transformation. Semantic validation: `StageSpecificVariableShouldHaveRationale` (stage specific variable should have rationale) in `mde/validation/psm/rules/compute.evl` the flag must be enabled for this rule to pass. | Either `true` or `false`. Example: `false`.                                                                                          |

### Relationships

| Relationship                | Kind and multiplicity | Meaning in the model                                                                                                         |
| --------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `value` → `ValueExpression` | containment, [1]      | The structured value expression supplying this setting; it may be literal, parameter-backed, resource-derived, or composite. |

## `LambdaInvocationBinding`

`LambdaInvocationBinding` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda invocation binding. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                          | Accepted values and example                |
| --------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `enabled` | `Boolean` [1]         | Whether this capability or connection is active in the modeled scenario. Keeping the switch explicit distinguishes an intentional omission from a temporarily disabled design. Within `LambdaInvocationBinding`, it applies to this specific element and its role in the surrounding model. | Either `true` or `false`. Example: `true`. |

### Relationships

This class declares no direct relationships.

## `SamFunctionEvent`

A SAM-level trigger owned by a Lambda function. Its event type determines how the contained native properties are rendered and how invocation reaches the target function.

Direct supertypes: `LambdaInvocationBinding`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                                                       | Accepted values and example                                                                                                 |
| ----------- | --------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `eventName` | `String` [1]          | The event name that identifies this sam function event in the model and its generated AWS configuration. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `SamFunctionEventExample`. |
| `eventType` | `String` [1]          | The SAM event-source type that gives meaning to this event's native properties.                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`.                |

### Relationships

| Relationship                           | Kind and multiplicity                           | Meaning in the model                                                                                                                                                                                                                                                                              |
| -------------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `properties` → `NativeProperty`        | containment, [*]                                | Provider-native properties retained for the selected SAM event or resource form.                                                                                                                                                                                                                  |
| `targetFunction` → `AwsLambdaFunction` | reference; read-only, [1]; opposite `samEvents` | The `targetFunction` reference on `SamFunctionEvent` identifies the destination represented by target function. An `AwsLambdaFunction` can remain independently owned and can participate in other parts of the model. Its opposite `samEvents` exposes the same connection from the target side. |

## `LambdaEventSourceMapping`

`LambdaEventSourceMapping` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda event source mapping. Its declaration gives the concept a precise home through destination config, function. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                      | Type and multiplicity  | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Accepted values and example                                                                                                                    |
| ------------------------------ | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `batchSize`                    | `Integer` [1]          | The maximum number of source records Lambda supplies in one invocation batch. Semantic validation: `BatchSizePositive` (batch size positive) in `mde/validation/psm/rules/compute.evl` the value must remain absent in this modeling situation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                        | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                               |
| `maximumBatchingWindowSeconds` | `Integer` [1]          | Records maximum batching window seconds duration or limit, expressed in seconds for lambda event source mapping. It keeps an operational boundary that should not be left to provider defaults explicit during review and transformation, so later steps do not have to infer it. Semantic validation: `MaximumBatchingWindowRange` (maximum batching window range) in `mde/validation/psm/rules/compute.evl` the value must remain absent in this modeling situation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                               |
| `startingPositionTimestamp`    | `String` [1]           | The point in time from which stream consumption begins when timestamp-based starting position is selected. Semantic validation: `StartingTimestampRequiresAtTimestamp` (starting timestamp requires at timestamp) in `mde/validation/psm/rules/compute.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `occurredAt`.                                 |
| `bisectBatchOnFunctionError`   | `Boolean` [1]          | The boolean decision for bisect batch on function error on this lambda event source mapping. It keeps an important design choice explicit for review and transformation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                               | Either `true` or `false`. Example: `false`.                                                                                                    |
| `maximumRecordAgeInSeconds`    | `Integer` [1]          | The numeric value used for maximum record age in seconds on this lambda event source mapping. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                                                                                                                                                                                                                                                                        | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                               |
| `maximumRetryAttempts`         | `Integer` [1]          | The numeric value used for maximum retry attempts on this lambda event source mapping. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                                                                                                                                                                                                                                                                               | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                               |
| `parallelizationFactor`        | `Integer` [1]          | Stores the parallelization factor value on the lambda event source mapping. The field records an operational boundary explicitly instead of leaving it to provider defaults. Semantic validation: `ParallelizationFactorRange` (parallelization factor range) in `mde/validation/psm/rules/compute.evl` the value must remain absent in this modeling situation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                       | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                               |
| `tumblingWindowInSeconds`      | `Integer` [1]          | The numeric value used for tumbling window in seconds on this lambda event source mapping. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                                                                                                                                                                                                                                                                           | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                               |
| `functionResponseTypes`        | `String` [*]           | Response-reporting modes enabled for the mapping, including partial batch failure reporting. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                           | A collection of values. Example: [`BUSINESS`, `BUSINESS-2`].                                                                                   |
| `filterCriteriaJson`           | `String` [1]           | The serialized representation of filter criteria on this lambda event source mapping. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                                                                                                                                                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                           |
| `reportBatchItemFailures`      | `Boolean` [1]          | The boolean decision for report batch item failures on this lambda event source mapping. It keeps an important design choice explicit for review and transformation.                                                                                                                                                                                                                                                                                                                                                                                                                        | Either `true` or `false`. Example: `false`.                                                                                                    |
| `maximumConcurrency`           | `Integer` [1]          | The concurrency ceiling reserved for this event-source mapping. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                                                                                                                                                        | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                               |
| `metricsConfig`                | `String` [1]           | The provider configuration for event-source mapping metrics.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Event Source Mapping Metrics Config`. |
| `startingPosition`             | `StartingPosition` [1] | The location in a stream from which a newly created mapping begins reading records. Semantic validation: `StartingTimestampRequiresAtTimestamp` (starting timestamp requires at timestamp) in `mde/validation/psm/rules/compute.evl` the feature participates in a semantic validation condition. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                      | Exactly one of: `TRIM_HORIZON`, `LATEST`, `AT_TIMESTAMP`. Example: `TRIM_HORIZON`.                                                             |

### Relationships

| Relationship                                    | Kind and multiplicity                                     | Meaning in the model                                                                                                                                                                                                                                       |
| ----------------------------------------------- | --------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `destinationConfig` → `LambdaDestinationConfig` | containment, [?]                                          | The `destinationConfig` containment on `LambdaEventSourceMapping` attaches the configuration record represented by destination config. The `LambdaDestinationConfig` objects are owned by `LambdaEventSourceMapping` and remain part of its model subtree. |
| `function` → `AwsLambdaFunction`                | reference; read-only, [1]; opposite `eventSourceMappings` | The Lambda function that participates in this connection.                                                                                                                                                                                                  |

## `SqsLambdaEventSourceMapping`

`SqsLambdaEventSourceMapping` is an AWS messaging record in the AWS platform-specific model. It carries the delivery setting for sqs lambda event source mapping. Its declaration gives the concept a precise home through queue. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `LambdaEventSourceMapping`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                          | Accepted values and example                                                                                                                                                                     |
| -------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `partialBatchFailureHandlingDecision`  | `Decision` [1]        | The explicit design decision on reporting individual failed SQS messages instead of retrying the complete batch. Semantic validation: `RequiredPartialBatchFailureDecisionHonored` (required partial batch failure decision honored) in `mde/validation/psm/rules/compute.evl` the feature participates in a semantic validation condition. | Exactly one of: `UNDECIDED`, `REQUIRED`, `NOT_REQUIRED`, `ACCEPTED`, `NEEDS_REVIEW`, `GENERATOR_OWNED`. Example: `UNDECIDED`.                                                                   |
| `partialBatchFailureHandlingRationale` | `String` [1]          | The explanation for choosing partial batch failure handling on this sqs lambda event source mapping. It preserves the design reasoning that cannot be recovered from the resulting value alone.                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `The choice protects the business outcome while keeping the design independently deployable.`. |

### Relationships

| Relationship         | Kind and multiplicity | Meaning in the model                                                                                      |
| -------------------- | --------------------- | --------------------------------------------------------------------------------------------------------- |
| `queue` → `SqsQueue` | reference, [1]        | The SQS queue used as the source, target, dead-letter destination, or protected resource in this context. |

## `DynamoDbStreamLambdaEventSourceMapping`

`DynamoDbStreamLambdaEventSourceMapping` is a DynamoDB deployment record in the AWS platform-specific model. It carries the storage setting for dynamo db stream lambda event source mapping. Its declaration gives the concept a precise home through table. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `LambdaEventSourceMapping`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship              | Kind and multiplicity | Meaning in the model                                              |
| ------------------------- | --------------------- | ----------------------------------------------------------------- |
| `table` → `DynamoDbTable` | reference, [1]        | The DynamoDB table whose stream supplies records to this mapping. |

## `GenericLambdaEventSourceMapping`

An escape hatch for supported Lambda polling sources that do not have a dedicated mapping subclass. The source kind and ARN expression state the provider binding, while `eventSourceResource` keeps an optional structural link.

Direct supertypes: `LambdaEventSourceMapping`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity       | What it captures and why it exists                                                                                                                                                            | Accepted values and example                                                                                                                               |
| --------------------- | --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `sourceKind`          | `LambdaEventSourceKind` [1] | The controlled value used for source kind on this generic lambda event source mapping. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. | Exactly one of: `SQS`, `DYNAMODB_STREAM`, `KINESIS_STREAM`, `MSK`, `SELF_MANAGED_KAFKA`, `DOCUMENTDB`, `MQ`, `OTHER`. Example: `SQS`.                     |
| `sourceArnExpression` | `String` [1]                | The expression used to determine source arn for this generic lambda event source mapping. It keeps executable or provider-interpreted logic visible to validation and generation.             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |

### Relationships

| Relationship                          | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                  |
| ------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `eventSourceResource` → `AwsResource` | reference, [?]        | The `eventSourceResource` reference on `GenericLambdaEventSourceMapping` identifies the source represented by event source resource. An `AwsResource` can remain independently owned and can participate in other parts of the model. |

## `LambdaDeadLetterConfig`

`LambdaDeadLetterConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda dead letter config. Its declaration gives the concept a precise home through target queue, target topic. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship               | Kind and multiplicity | Meaning in the model                                                                                                                                                                                         |
| -------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `targetQueue` → `SqsQueue` | reference, [?]        | The `targetQueue` reference on `LambdaDeadLetterConfig` identifies the destination represented by target queue. A `SqsQueue` can remain independently owned and can participate in other parts of the model. |
| `targetTopic` → `SnsTopic` | reference, [?]        | The `targetTopic` reference on `LambdaDeadLetterConfig` identifies the destination represented by target topic. A `SnsTopic` can remain independently owned and can participate in other parts of the model. |

## `LambdaEventInvokeConfig`

`LambdaEventInvokeConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda event invoke config. Its declaration gives the concept a precise home through destination config, function, qualifier alias. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                  | Type and multiplicity | What it captures and why it exists                                                                                                                                                                 | Accepted values and example                                                                      |
| -------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `maximumRetryAttempts`     | `Integer` [1]         | The numeric value used for maximum retry attempts on this lambda event invoke config. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.       | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |
| `maximumEventAgeInSeconds` | `Integer` [1]         | The numeric value used for maximum event age in seconds on this lambda event invoke config. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |

### Relationships

| Relationship                                    | Kind and multiplicity                                    | Meaning in the model                                                                                                                                                                                                                                     |
| ----------------------------------------------- | -------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `destinationConfig` → `LambdaDestinationConfig` | containment, [?]                                         | The `destinationConfig` containment on `LambdaEventInvokeConfig` attaches the configuration record represented by destination config. The `LambdaDestinationConfig` objects are owned by `LambdaEventInvokeConfig` and remain part of its model subtree. |
| `function` → `AwsLambdaFunction`                | reference; read-only, [1]; opposite `eventInvokeConfigs` | The Lambda function that participates in this connection.                                                                                                                                                                                                |
| `qualifierAlias` → `LambdaAlias`                | reference, [?]                                           | The function alias whose asynchronous invocation settings this resource configures.                                                                                                                                                                      |

## `LambdaDestinationConfig`

`LambdaDestinationConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda destination config. Its declaration gives the concept a precise home through on success, on failure. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                | Kind and multiplicity | Meaning in the model                                           |
| --------------------------- | --------------------- | -------------------------------------------------------------- |
| `onSuccess` → `AwsResource` | reference, [?]        | The destination used after successful asynchronous invocation. |
| `onFailure` → `AwsResource` | reference, [?]        | The destination used after asynchronous invocation failure.    |

## `LambdaTracingConfig`

`LambdaTracingConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda tracing config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TracingConfig`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity   | What it captures and why it exists                                                                                                                                       | Accepted values and example                                              |
| --------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------ |
| `mode`    | `LambdaTracingMode` [1] | The controlled value used for mode on this lambda tracing config. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. | Exactly one of: `ACTIVE`, `PASS_THROUGH`, `DISABLED`. Example: `ACTIVE`. |

### Relationships

This class declares no direct relationships.

## `LambdaLoggingConfig`

`LambdaLoggingConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda logging config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                   | Accepted values and example                                                                                                          |
| --------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `applicationLogLevel` | `String` [1]          | The minimum application-log severity emitted through Lambda advanced logging controls.                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `INFO`.                             |
| `systemLogLevel`      | `String` [1]          | The minimum severity retained for Lambda platform and runtime-system logs. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.      | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `INFO`.                             |
| `logFormat`           | `String` [1]          | The text or structured JSON format used for Lambda application and system logs. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Logging Config Log Format`. |
| `logGroupName`        | `String` [1]          | The log group name that identifies this lambda logging config in the model and its generated AWS configuration.                                                                                      | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `LambdaLoggingConfigExample`.       |

### Relationships

This class declares no direct relationships.

## `LambdaLayerVersion`

`LambdaLayerVersion` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda layer version. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                 | Type and multiplicity    | What it captures and why it exists                                                                                                                                                                                                                                                                               | Accepted values and example                                                                                                           |
| ------------------------- | ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `layerName`               | `String` [1]             | The layer name that identifies this lambda layer version in the model and its generated AWS configuration. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `LambdaLayerVersionExample`.         |
| `contentUri`              | `String` [1]             | The location of the archive containing this Lambda layer.                                                                                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `https://example.com/orders`.        |
| `compatibleRuntimes`      | `String` [*]             | The runtimes for which the layer declares compatibility.                                                                                                                                                                                                                                                         | A collection of values. Example: [`Lambda Layer Version Compatible Runtimes`, `Lambda Layer Version Compatible Runtimes-2`].          |
| `licenseInfo`             | `String` [1]             | The licence identifier or location published with the layer version.                                                                                                                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Layer Version License Info`. |
| `compatibleArchitectures` | `LambdaArchitecture` [*] | The controlled value used for compatible architectures on this lambda layer version. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A collection containing only these literals: `X86_64`, `ARM64`. Example: [`X86_64`].                                                  |

### Relationships

This class declares no direct relationships.

## `LambdaLayerPermission`

`LambdaLayerPermission` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda layer permission. Its declaration gives the concept a precise home through layer version. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                                                                                                                                                              | Accepted values and example                                                                                                           |
| ---------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `action`         | `String` [1]          | The layer-version access action granted by this permission.                                                                                                                                                     | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Layer Permission Action`.    |
| `principal`      | `String` [1]          | The AWS account or public principal allowed to obtain the layer version.                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Layer Permission Principal`. |
| `organizationId` | `String` [1]          | An AWS Organizations identifier that limits layer access to accounts in that organization. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `order-123`.                         |

### Relationships

| Relationship                          | Kind and multiplicity | Meaning in the model                                  |
| ------------------------------------- | --------------------- | ----------------------------------------------------- |
| `layerVersion` → `LambdaLayerVersion` | reference, [1]        | The Lambda layer version governed by this permission. |

## `LambdaVersion`

`LambdaVersion` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda version. Its declaration gives the concept a precise home through provisioned concurrency config, function. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                            | Accepted values and example                                                                             |
| -------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `versionDescription` | `String` [1]          | Human-readable release context attached to the published Lambda version. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `1.0`. |

### Relationships

| Relationship                                                          | Kind and multiplicity                          | Meaning in the model                                                                                                                                                                                                                                                   |
| --------------------------------------------------------------------- | ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `provisionedConcurrencyConfig` → `LambdaProvisionedConcurrencyConfig` | containment, [?]                               | The `provisionedConcurrencyConfig` containment on `LambdaVersion` attaches the configuration record represented by provisioned concurrency config. The `LambdaProvisionedConcurrencyConfig` objects are owned by `LambdaVersion` and remain part of its model subtree. |
| `function` → `AwsLambdaFunction`                                      | reference; read-only, [1]; opposite `versions` | The Lambda function that participates in this connection.                                                                                                                                                                                                              |

## `LambdaAlias`

`LambdaAlias` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda alias. Its declaration gives the concept a precise home through provisioned concurrency config, function, version. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute         | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                              | Accepted values and example                                                                                                     |
| ----------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `aliasName`       | `String` [1]          | The alias name that identifies this lambda alias in the model and its generated AWS configuration.                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `LambdaAliasExample`.          |
| `functionVersion` | `String` [1]          | The published function version to which this alias sends invocations.                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `1.0`.                         |
| `routingConfig`   | `String` [1]          | The weighted routing configuration used to shift a fraction of alias traffic to another published version. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Alias Routing Config`. |

### Relationships

| Relationship                                                          | Kind and multiplicity                         | Meaning in the model                                                                                                                                                                                                                                               |
| --------------------------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `provisionedConcurrencyConfig` → `LambdaProvisionedConcurrencyConfig` | containment, [?]                              | The `provisionedConcurrencyConfig` containment on `LambdaAlias` attaches the configuration record represented by provisioned concurrency config. The `LambdaProvisionedConcurrencyConfig` objects are owned by `LambdaAlias` and remain part of its model subtree. |
| `function` → `AwsLambdaFunction`                                      | reference; read-only, [1]; opposite `aliases` | The Lambda function that participates in this connection.                                                                                                                                                                                                          |
| `version` → `LambdaVersion`                                           | reference, [?]                                | The published Lambda version addressed by this alias.                                                                                                                                                                                                              |

## `LambdaProvisionedConcurrencyConfig`

`LambdaProvisionedConcurrencyConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda provisioned concurrency config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                         | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                 | Accepted values and example                                                                      |
| --------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| `provisionedConcurrentExecutions` | `Integer` [1]         | The numeric value used for provisioned concurrent executions on this lambda provisioned concurrency config. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |

### Relationships

This class declares no direct relationships.

## `LambdaPermission`

`LambdaPermission` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda permission. Its declaration gives the concept a precise home through function. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity           | What it captures and why it exists                                                                                                                                                     | Accepted values and example                                                                                                                               |
| --------------------- | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `action`              | `String` [1]                    | The Lambda API action granted to the invoking principal.                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Permission Action`.                              |
| `principal`           | `String` [1]                    | The AWS service or account allowed to invoke the function.                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Permission Principal`.                           |
| `sourceArn`           | `String` [1]                    | The source-resource ARN used to limit the principal's invocation grant.                                                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `sourceAccount`       | `String` [1]                    | The AWS account constraint applied to the invoking source, reducing cross-account confused-deputy risk.                                                                                | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Lambda Permission Source Account`.                      |
| `functionUrlAuthType` | `LambdaFunctionUrlAuthType` [1] | The controlled value used for function url auth type on this lambda permission. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. | Exactly one of: `NONE`, `AWS_IAM`. Example: `NONE`.                                                                                                       |

### Relationships

| Relationship                     | Kind and multiplicity                             | Meaning in the model                                      |
| -------------------------------- | ------------------------------------------------- | --------------------------------------------------------- |
| `function` → `AwsLambdaFunction` | reference; read-only, [1]; opposite `permissions` | The Lambda function that participates in this connection. |

## `LambdaFunctionUrl`

`LambdaFunctionUrl` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda function url. Its declaration gives the concept a precise home through cors, function. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute    | Type and multiplicity           | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                      | Accepted values and example                                         |
| ------------ | ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `authType`   | `LambdaFunctionUrlAuthType` [1] | The authentication policy enforced at the function URL boundary. Semantic validation: `ProductionFunctionUrlRequiresAuth` (production function url requires auth) in `mde/validation/psm/rules/compute.evl` the value must satisfy a numeric or ordering boundary. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Exactly one of: `NONE`, `AWS_IAM`. Example: `NONE`.                 |
| `invokeMode` | `LambdaInvokeMode` [1]          | The controlled value used for invoke mode on this lambda function url. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                                           | Exactly one of: `BUFFERED`, `RESPONSE_STREAM`. Example: `BUFFERED`. |

### Relationships

| Relationship                          | Kind and multiplicity                             | Meaning in the model                                                       |
| ------------------------------------- | ------------------------------------------------- | -------------------------------------------------------------------------- |
| `cors` → `LambdaUrlCorsConfiguration` | containment, [?]                                  | The owned cross-origin policy controlling browser access to this endpoint. |
| `function` → `AwsLambdaFunction`      | reference; read-only, [1]; opposite `functionUrl` | The Lambda function that participates in this connection.                  |

## `LambdaUrlCorsConfiguration`

`LambdaUrlCorsConfiguration` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda url cors configuration. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `CorsConfiguration`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `LambdaFileSystemConfig`

`LambdaFileSystemConfig` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda file system config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                     | Accepted values and example                                                                                                                               |
| ---------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `arn`            | `String` [1]          | The EFS access-point ARN mounted into the Lambda execution environment. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `localMountPath` | `String` [1]          | The absolute path at which the file system is mounted inside the Lambda environment.                                                                                                                                                                                                                   | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`.                                     |

### Relationships

This class declares no direct relationships.

## `CodeSigningConfig`

`CodeSigningConfig` is a configuration record in the AWS platform-specific model. It makes code signing config explicit. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                           | Accepted values and example                                                                                                                   |
| --------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `allowedPublishers`   | `String` [1]          | The signing-profile identities whose signed deployment packages are accepted by this code-signing configuration. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Code Signing Config Allowed Publishers`.    |
| `codeSigningPolicies` | `String` [1]          | The policy deciding how Lambda reacts when a package fails signature checks. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Code Signing Config Code Signing Policies`. |
| `descriptionText`     | `String` [1]          | The provider-facing descriptive text emitted for this resource. It gives an operator context in the generated template and console without carrying runtime behavior. Within `CodeSigningConfig`, it applies to this specific element and its role in the surrounding model. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`.                         |

### Relationships

This class declares no direct relationships.
